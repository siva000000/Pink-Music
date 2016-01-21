//
// PinkMusic is distributed under the FreeBSD License
//
// Copyright (c) 2013-2015, Siva Prasad
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// The views and conclusions contained in the software and documentation are those
// of the authors and should not be interpreted as representing official policies,
// either expressed or implied, of the FreeBSD Project.
//
// https://github.com/siva000000/Pink-Music
//

//https://www.khronos.org/opengles/sdk/docs/man31/html/glDeleteBuffers.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glVertexAttribPointer.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glTexImage2D.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glGenBuffers.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glPixelStorei.xhtml
//https://www.khronos.org/opengles/sdk/docs/man31/html/glUniform.xhtml

#include <android/bitmap.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

//These constants must be kept synchronized with the ones in Java
#define ERR_INFO -1
#define ERR_FORMAT -2
#define ERR_LOCK -3
#define ERR_NOMEM -4
#define ERR_GL -5

#define TYPE_SPECTRUM 0
#define TYPE_LIQUID 1
#define TYPE_SPIN 2
#define TYPE_PARTICLE 3
#define TYPE_IMMERSIVE_PARTICLE 4
#define TYPE_IMMERSIVE_PARTICLE_VR 5

#define left -1.0f
#define top 1.0f
#define right 1.0f
#define bottom -1.0f
#define z 0.0f
static const float glVerticesRect[] = {
	left, bottom, z, 1.0f,
	right, bottom, z, 1.0f,
	left, top, z, 1.0f,
	right, top, z, 1.0f
};
#undef left
#undef top
#undef right
#undef bottom
#undef z
#define leftTex 0.0f
#define topTex 0.0f
#define rightTex 1.0f
#define bottomTex 1.0f
static const float glTexCoordsRect[] = {
	leftTex, bottomTex,
	rightTex, bottomTex,
	leftTex, topTex,
	rightTex, topTex
};
#undef leftTex
#undef topTex
#undef rightTex
#undef bottomTex

static const char* const rectangleVShader = "attribute vec4 inPosition; attribute vec2 inTexCoord; varying vec2 vTexCoord; void main() { gl_Position = inPosition; vTexCoord = inTexCoord; }";
static const char* const textureFShader = "precision mediump float; varying vec2 vTexCoord; uniform sampler2D texColor; void main() { gl_FragColor = texture2D(texColor, vTexCoord); }";
static const char* const textureFShaderOES = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vTexCoord; uniform samplerExternalOES texColorOES; void main() { gl_FragColor = texture2D(texColorOES, vTexCoord); }";

static unsigned int glProgram, glProgram2, glType, glBuf[5];
static int glTime, glAmplitude, glVerticesPerRow, glRows, glMatrix, glPos, glColor, glBaseX, glTheta, glOESTexture;

float glSmoothStep(float edge0, float edge1, float x) {
	float t = (x - edge0) / (edge1 - edge0);
	return ((t <= 0.0f) ? 0.0f :
		((t >= 1.0f) ? 1.0f :
			(t * t * (3.0f - (2.0f * t)))
		)
	);
}

#include "GLSoundParticle.h"

int createProgram(const char* vertexShaderSource, const char* fragmentShaderSource, unsigned int* program) {
	int l;
	unsigned int p, vertexShader, fragmentShader;

	p = glCreateProgram();
	if (glGetError() || !p) return -1;
	vertexShader = glCreateShader(GL_VERTEX_SHADER);
	if (glGetError() || !vertexShader) return -2;
	fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
	if (glGetError() || !fragmentShader) return -3;

	l = strlen(vertexShaderSource);
	glShaderSource(vertexShader, 1, &vertexShaderSource, &l);
	if (glGetError()) return -4;

	l = strlen(fragmentShaderSource);
	glShaderSource(fragmentShader, 1, &fragmentShaderSource, &l);
	if (glGetError()) return -5;

	glCompileShader(vertexShader);
	l = 0;
	glGetShaderiv(vertexShader, GL_COMPILE_STATUS, &l);
	//int s = 0;
	//glGetShaderInfoLog(vertexShader, 1024, &s, (char*)floatBuffer);
	//((char*)floatBuffer)[s] = 0;
	//__android_log_print(ANDROID_LOG_INFO, "JNI", "Compilation result: %s", (char*)floatBuffer);
	if (glGetError() || !l) return -6;

	glCompileShader(fragmentShader);
	l = 0;
	glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, &l);
	if (glGetError() || !l) return -7;

	glAttachShader(p, vertexShader);
	if (glGetError()) return -8;
	glAttachShader(p, fragmentShader);
	if (glGetError()) return -9;

	*program = p;

	return 0;
}

int computeSpinSize(int width, int height, int dp1OrLess) {
	dp1OrLess = (dp1OrLess ? 10 : 20);
	int size = dp1OrLess;
	while (size < 33 && ((width % size) || (height % size)))
		size++;
	if (size > 32) {
		size = dp1OrLess;
		while (size < 33 && (height % size))
			size++;
		if (size > 32) {
			size = dp1OrLess;
			while (size < 33 && (width % size))
				size++;
			if (size > 32)
				size = dp1OrLess;
		}
	}
	return size;
}

int JNICALL glGetOESTexture(JNIEnv* env, jclass clazz) {
	return glOESTexture;
}

int JNICALL glOnSurfaceCreated(JNIEnv* env, jclass clazz, int bgColor, int type, int estimatedWidth, int estimatedHeight, int dp1OrLess, int hasGyro) {
	commonSRand();
	glType = type;
	glProgram = 0;
	glProgram2 = 0;
	glBuf[0] = 0;
	glBuf[1] = 0;
	glBuf[2] = 0;
	glBuf[3] = 0;
	glBuf[4] = 0;
	glTime = 0;
	glAmplitude = 0;
	glVerticesPerRow = 0;
	glRows = 0;
	glMatrix = 0;
	glPos = 0;
	glColor = 0;
	glBaseX = 0;
	glTheta = 0;
	glOESTexture = 0;
	commonTime = 0;
	switch (type) {
	case TYPE_LIQUID:
		commonTimeLimit = 12566; //2 * 2 * pi * 1000
		break;
	case TYPE_SPIN:
		commonTimeLimit = 6283; //2 * pi * 1000
		break;
	case TYPE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE_VR:
		commonTimeLimit = 0xffffffff;
		break;
	default:
		commonTimeLimit = 0xffffffff;
		glGetIntegerv(GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, &glTime);
		if (glTime < 2)
			glTime = 0;
		break;
	}

	int l;
	unsigned int glTex[2];
	const char* vertexShader;
	const char* fragmentShader;

	switch (type) {
	case TYPE_LIQUID:
		vertexShader = "attribute float inPosition; attribute float inTexCoord; varying vec2 vTexCoord; varying float vAmpl; uniform float amplitude[33]; void main() {" \
		"vec2 coord = vec2((inPosition + 1.0) * 0.5, 0.0);" \
		"float absy;" \
		"if (inTexCoord < 0.0) {" \
			"vAmpl = 0.0;" \
			"gl_Position = vec4(inPosition, 1.0, 0.0, 1.0);" \
		"} else {" \
			"int i = int(inTexCoord);" \
			"absy = amplitude[i];" \
			"absy += (amplitude[i + 1] - absy) * smoothstep(0.0, 1.0, fract(inTexCoord));" \
			"vAmpl = 1.0;" \
			"gl_Position = vec4(inPosition, (absy * 2.0) - 1.0, 0.0, 1.0);" \
			"coord.y = 1.0 - absy;" \
		"}" \
		"vTexCoord = coord; }";
		break;
	case TYPE_SPIN:
		//inPosition stores the distance of the vertex to the origin in the z component
		//inTexCoord stores the angle of the vertex in the z component
		vertexShader = "attribute vec3 inPosition; attribute vec3 inTexCoord; varying vec2 vTexCoord; varying vec3 vColor; varying float dist; uniform float amplitude[33]; uniform float time; void main() {" \
		"gl_Position = vec4(inPosition.x, inPosition.y, 0.0, 1.0);" \
		"float d = inPosition.z;" \

		"vTexCoord = inTexCoord.xy;" \
		"float angle = inTexCoord.z - (0.25 * amplitude[int(d * 31.9375)]);" \

		"dist = d * d * (0.5 + (1.5 * amplitude[2]));" \

		"vColor = vec3(abs(cos(angle*5.0+time))," \
		"abs(cos(angle*7.0+time*2.0))," \
		"abs(cos(angle*11.0+time*4.0))" \
		");" \

		"}";
		break;
	case TYPE_PARTICLE:
		vertexShader = "attribute vec4 inPosition; attribute vec2 inTexCoord; attribute float inIndex; varying vec2 vTexCoord; varying vec3 vColor; uniform float amplitude; uniform float baseX; uniform vec2 posArr[16]; uniform vec2 aspect; uniform vec3 colorArr[16]; uniform float thetaArr[16]; void main() {" \
		"int idx = int(inIndex);" \
		"vec2 pos = posArr[idx];" \
		"float a = mix(0.0625, 0.34375, amplitude);" \
		"float bottom = 1.0 - clamp(pos.y, -1.0, 1.0);" \
		"bottom = bottom * bottom * bottom * 0.125;" \
		"a = (0.75 * a) + (0.25 * bottom);" \
		"gl_Position = vec4(baseX + pos.x + (5.0 * (pos.y + 1.0) * pos.x * sin((2.0 * pos.y) + thetaArr[idx])) + (inPosition.x * aspect.x * a), pos.y + (inPosition.y * aspect.y * a), 0.0, 1.0);" \
		"vTexCoord = inTexCoord;" \
		"vColor = colorArr[idx] + bottom + (0.25 * amplitude);" \
		"}";
		break;
	case TYPE_IMMERSIVE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE_VR:
		vertexShader = "attribute vec2 inPosition; attribute vec2 inTexCoord; attribute float inIndex; varying vec2 vTexCoord; varying vec3 vColor; uniform float amplitude; uniform float diffusion; uniform float baseX; uniform vec2 posArr[16]; uniform vec2 aspect; uniform vec3 colorArr[16]; uniform float thetaArr[16]; uniform mat4 mvpMat; void main() {" \
		"int idx = int(inIndex);" \
		"vec2 pos = posArr[idx];" \
		/*start with the original computation*/ \
		"float a = mix(0.0625, 0.484375, amplitude)," \
			"bottom = 1.0 - clamp(pos.y, -1.0, 1.0);" \
		"bottom = bottom * bottom * bottom * 0.125;" \
		/*now that the particles are spread in a full circle, I decided*/ \
		/*to increase their size by 50% (I also moved the "* 5" here) */ \
		/*"a = (0.75 * a) + (0.25 * bottom);"*/ \
		"a = (4.125 * a) + (1.375 * bottom);" \

		"vec3 smoothedColor = colorArr[idx] + bottom + (0.25 * amplitude);" \
		/*make the particles smoothly appear at the bottom and diminish at the top*/ \
		/*(from here on, bottom will store the particle's distance from the center - the radius)*/ \
		"if (pos.y > 0.0) {" \
			/*let's make the radius decrease from 3 to 1 at the top*/ \
			"bottom = 3.0 - (2.0 * pos.y);" \
			/*make the particles smaller as they approach the top (y > 0.8)*/ \
			"if (pos.y > 0.9)" \
				"a *= 1.0 - ((pos.y - 0.9) / 0.3);" \
		"} else if (pos.y < -0.8) {" \
			"bottom = smoothstep(-1.2, -0.8, pos.y);" \
			/*make the particles larger than usual at the bottom*/ \
			/*(they will sprout 50% bigger)*/ \
			"a *= (1.5 - (0.5 * bottom));" \
			"smoothedColor *= bottom;" \
			"bottom *= 3.0;" \
		"} else {" \
			"bottom = 3.0;" \
		"}" \
		"vTexCoord = inTexCoord;" \
		"vColor = smoothedColor;" \

		/*baseX goes from -0.9 / 0.9, which we will map to a full circle */ \
		/*(using 1.7 instead of 3.14 maps to pi / 0)*/ \
		"smoothedColor.x = -3.14 * (baseX + pos.x + (diffusion * (pos.y + 1.0) * pos.x * sin((2.0 * pos.y) + thetaArr[idx])));" \
		/*spread the particles in a semicylinder with a radius of 3 and height of 12*/ \
		"vec4 p = mvpMat * vec4(bottom * cos(smoothedColor.x), bottom * sin(smoothedColor.x), 6.0 * pos.y, 1.0);" \
		/*"vec4 p = mvpMat * vec4(bottom * cos(smoothedColor.x) + (inPosition.x * a * 5.0), bottom * sin(smoothedColor.x) + (inPosition.y * a * 5.0), 6.0 * pos.y, 1.0);"*/ \

		/*gl_Position is different from p, because we want the particles to be always facing the camera*/ \
		"gl_Position = vec4(p.x + (inPosition.x * aspect.x * a), p.y + (inPosition.y * aspect.y * a), p.z, p.w);" \
		"}";
		break;
	default:
		vertexShader = (glTime ? "attribute float inPosition; varying vec4 vColor; uniform sampler2D texAmplitude; uniform sampler2D texColor; void main() {" \
		"float absx = abs(inPosition);" \
		"vec4 c = texture2D(texAmplitude, vec2(0.5 * (absx - 1.0), 0.0));" \
		"gl_Position = vec4(absx - 2.0, sign(inPosition) * c.a, 0.0, 1.0);" \
		"vColor = texture2D(texColor, c.ar); }"
		:
		//Tegra GPUs CANNOT use textures in vertex shaders AND does not support more than 256 registers! :(
		//http://stackoverflow.com/questions/11398114/vertex-shader-doesnt-run-on-galaxy-tab10-tegra-2
		//http://developer.download.nvidia.com/assets/mobile/files/tegra_gles2_development.pdf
		//https://www.khronos.org/registry/gles/specs/2.0/GLSL_ES_Specification_1.0.17.pdf page 111-113
		"attribute float inPosition; varying float vAmpl; uniform float amplitude[128]; void main() {" \
		"float absx = abs(inPosition);" \
		"float c = amplitude[int(floor(63.5 * (absx - 1.0)))];" \
		"gl_Position = vec4(absx - 2.0, sign(inPosition) * c, 0.0, 1.0);" \
		"vAmpl = c; }");
		break;
	}

	switch (type) {
	case TYPE_LIQUID:
		fragmentShader = "precision highp float; varying vec2 vTexCoord; varying float vAmpl; uniform sampler2D texColor; uniform float time; void main() {" \

		/* This water equation (length, sin, cos) was based on: http://glslsandbox.com/e#21421.0 */ \
		"vec2 p = (vec2(vTexCoord.x, mix(vTexCoord.y, vAmpl, 0.25)) * 6.0) - vec2(125.0);" \

		/* Let's perform only one iteration, in favor of speed ;) */ \
		"float t = time * -0.5;" \
		"vec2 i = p + vec2(cos(t - p.x) + sin(t + p.y), sin(t - p.y) + cos(t + p.x));" \
		"float c = 1.0 + (1.0 / length(vec2(p.x / (sin(i.x + t) * 100.0), p.y / (cos(i.y + t) * 100.0))));" \

		"c = 1.5 - sqrt(c);" \

		"c = 1.25 * c * c * c;" \
		"t = (vAmpl * vAmpl * vAmpl);" \

		"gl_FragColor = (0.5 * t) + (0.7 * vec4(c, c + 0.1, c + 0.2, 0.0)) + texture2D(texColor, vec2(vTexCoord.x, vTexCoord.y * (1.0 - (min(1.0, (1.2 * c) + t) * 0.55))));" \

		"}";
		break;
	case TYPE_SPIN:
		fragmentShader = "precision mediump float; varying vec2 vTexCoord; varying vec3 vColor; varying float dist; uniform sampler2D texColor; void main() {" \
		"float c = dist - texture2D(texColor, vTexCoord).a;" \
		"gl_FragColor = vec4(vColor.r + c, vColor.g + c, vColor.b + c, 1.0);" \
		"}";
		break;
	case TYPE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE_VR:
		fragmentShader = "precision mediump float; varying vec2 vTexCoord; varying vec3 vColor; uniform sampler2D texColor; void main() {" \
		"float a = texture2D(texColor, vTexCoord).a;"
		"gl_FragColor = vec4(vColor.r * a, vColor.g * a, vColor.b * a, 1.0);" \
		"}";
		break;
	default:
		fragmentShader = (glTime ? "precision mediump float; varying vec4 vColor; void main() { gl_FragColor = vColor; }"
		:
		//Tegra GPUs CANNOT use textures in vertex shaders! :(
		//http://stackoverflow.com/questions/11398114/vertex-shader-doesnt-run-on-galaxy-tab10-tegra-2
		//http://developer.download.nvidia.com/assets/mobile/files/tegra_gles2_development.pdf
		"precision mediump float; varying float vAmpl; uniform sampler2D texColor; void main() { gl_FragColor = texture2D(texColor, vec2(vAmpl, 0.0)); }");
		break;
	}

	if ((l = createProgram(vertexShader, fragmentShader, &glProgram)))
		return l;

	glBindAttribLocation(glProgram, 0, "inPosition");
	if (glGetError()) return -10;
	if (type != TYPE_SPECTRUM) {
		glBindAttribLocation(glProgram, 1, "inTexCoord");
		if (glGetError()) return -11;
	}
	glLinkProgram(glProgram);
	if (glGetError()) return -12;

	if (type == TYPE_LIQUID) {
		if ((l = createProgram(
			rectangleVShader,
			textureFShader,
			&glProgram2)))
			return l;

		glBindAttribLocation(glProgram2, 2, "inPosition");
		if (glGetError()) return -10;
		glBindAttribLocation(glProgram2, 3, "inTexCoord");
		if (glGetError()) return -11;
		glLinkProgram(glProgram2);
		if (glGetError()) return -12;

		glGenBuffers(4, glBuf);
		if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2] || !glBuf[3]) return -13;

		//in order to save memory and bandwidth, we are sending only the x coordinate
		//of each point, but with a few modifications...
		//
		//this array represents a horizontal triangle strip with x ranging from -1 to 1,
		//and which vertices are ordered like this:
		// 1  3  5  7
		//             ...
		// 0  2  4  6
		float *vertices = new float[1024];

		for (int i = 0; i < 512; i++) {
			const float p = -1.0f + (2.0f * (float)i / 511.0f);
			vertices[(i << 1)    ] = p;
			vertices[(i << 1) + 1] = p;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

		//vertices at odd indices receive a -1, to indicate they are static, and must be placed
		//at the top of the screen
		for (int i = 1; i < 1024; i += 2)
			vertices[i] = -1.0f;
		//vertices at even indices receive a value in range [0 .. 32[
		for (int i = 0; i < 1024; i += 2)
			vertices[i] = (float)(i << 5) / 1024.0f;
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glBufferData(GL_ARRAY_BUFFER, (512 * 2) * sizeof(float), vertices, GL_STATIC_DRAW);

		delete vertices;

		//create a rectangle that occupies the entire screen
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 4) * sizeof(float), glVerticesRect, GL_STATIC_DRAW);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);

		if (glGetError()) return -14;
	} else if (type == TYPE_SPIN) {
		glGenBuffers(2, glBuf);
		if (glGetError() || !glBuf[0] || !glBuf[1]) return -13;
	} else if (type == TYPE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE_VR) {
		glBindAttribLocation(glProgram, 2, "inIndex");
        if (glGetError()) return -12;
        if (type == TYPE_IMMERSIVE_PARTICLE_VR) {
			//create a second program to render the camera preview
			if ((l = createProgram(
				rectangleVShader,
				textureFShaderOES,
				&glProgram2)))
				return l;

			glBindAttribLocation(glProgram2, 3, "inPosition");
			if (glGetError()) return -10;
			glBindAttribLocation(glProgram2, 4, "inTexCoord");
			if (glGetError()) return -11;
			glLinkProgram(glProgram2);
			if (glGetError()) return -12;

			glGenBuffers(5, glBuf);
			if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2] || !glBuf[3] || !glBuf[4]) return -13;

			//create a rectangle that occupies the entire screen
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
			glBufferData(GL_ARRAY_BUFFER, (4 * 4) * sizeof(float), glVerticesRect, GL_STATIC_DRAW);
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[4]);
			glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);

			if (glGetError()) return -14;
		} else {
			glGenBuffers(3, glBuf);
			if (glGetError() || !glBuf[0] || !glBuf[1] || !glBuf[2]) return -13;
		}

		if (glSoundParticle)
			delete glSoundParticle;
		glSoundParticle = new GLSoundParticle(hasGyro);

		//create a rectangle for the particles, cropping a 10-pixel border, to improve speed
#define left (-(32.0f - 10.0f) / 32.0f)
#define top ((32.0f - 10.0f) / 32.0f)
#define right ((32.0f - 10.0f) / 32.0f)
#define bottom (-(32.0f - 10.0f) / 32.0f)
		//let's create BG_PARTICLES_BY_COLUMN copies of the same rectangle, divided into 2 triangles
		for (int i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
			const int idx = i * (3 * 2 * 2);
			//triangle 1 (CCW)
			floatBuffer[idx + 0 ] = left;
			floatBuffer[idx + 1 ] = bottom;
			floatBuffer[idx + 2 ] = right;
			floatBuffer[idx + 3 ] = top;
			floatBuffer[idx + 4 ] = left;
			floatBuffer[idx + 5 ] = top;

			//triangle 2 (CCW)
			floatBuffer[idx + 6 ] = left;
			floatBuffer[idx + 7 ] = bottom;
			floatBuffer[idx + 8 ] = right;
			floatBuffer[idx + 9 ] = bottom;
			floatBuffer[idx + 10] = right;
			floatBuffer[idx + 11] = top;
		}
#undef left
#undef top
#undef right
#undef bottom
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 3 * 2 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
#define left (10.0f / 64.0f)
#define top (10.0f / 64.0f)
#define right (54.0f / 64.0f)
#define bottom (54.0f / 64.0f)
		//let's create BG_PARTICLES_BY_COLUMN copies of the same rectangle, divided into 2 triangles
		for (int i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
			const int idx = i * (3 * 2 * 2);
			//triangle 1 (CCW)
			floatBuffer[idx + 0 ] = left;
			floatBuffer[idx + 1 ] = bottom;
			floatBuffer[idx + 2 ] = right;
			floatBuffer[idx + 3 ] = top;
			floatBuffer[idx + 4 ] = left;
			floatBuffer[idx + 5 ] = top;

			//triangle 2 (CCW)
			floatBuffer[idx + 6 ] = left;
			floatBuffer[idx + 7 ] = bottom;
			floatBuffer[idx + 8 ] = right;
			floatBuffer[idx + 9 ] = bottom;
			floatBuffer[idx + 10] = right;
			floatBuffer[idx + 11] = top;
		}
#undef left
#undef top
#undef right
#undef bottom
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 3 * 2 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
		//now let's fill in the buffer used to store the indices
		for (int i = 0; i < BG_PARTICLES_BY_COLUMN; i++) {
			const float f = (float)i;
			const int idx = i * (3 * 2);
			//triangle 1 (CCW)
			floatBuffer[idx + 0] = f;
			floatBuffer[idx + 1] = f;
			floatBuffer[idx + 2] = f;

			//triangle 2 (CCW)
			floatBuffer[idx + 3] = f;
			floatBuffer[idx + 4] = f;
			floatBuffer[idx + 5] = f;
		}
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glBufferData(GL_ARRAY_BUFFER, (BG_PARTICLES_BY_COLUMN * 3 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);

		if (glGetError()) return -14;

		glClearColor((float)((bgColor >> 16) & 0xff) / 255.0f, (float)((bgColor >> 8) & 0xff) / 255.0f, (float)(bgColor & 0xff) / 255.0f, 1.0f);
	} else {
		glGenBuffers(1, glBuf);
		if (glGetError() || !glBuf[0]) return -13;

		//in order to save memory and bandwidth, we are sending only the x coordinate
		//of each point, but with a few modifications...
		//
		//this array represents a horizontal triangle strip with x ranging from 1 to 3,
		//and which vertices are ordered like this:
		// 1  3  5  7
		//             ...
		// 0  2  4  6
		//
		//values at even indexes receive a negative value, so that sign() returns -1
		//in the vertex shader
		//
		//we cannot send x = 0, as sign(0) = 0, and this would render that point useless
		if (glTime) {
			for (int i = 0; i < 256; i++) {
				//even is negative, odd is positive
				const float p = 1.0f + (2.0f * (float)i / 255.0f);
				floatBuffer[(i << 1)    ] = -p;
				floatBuffer[(i << 1) + 1] = p;
			}
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
			glBufferData(GL_ARRAY_BUFFER, (256 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
		} else {
			for (int i = 0; i < 128; i++) {
				//even is negative, odd is positive
				const float p = 1.0f + (2.0f * (float)i / 127.0f);
				floatBuffer[(i << 1)    ] = -p;
				floatBuffer[(i << 1) + 1] = p;
			}
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
			glBufferData(GL_ARRAY_BUFFER, (128 * 2) * sizeof(float), floatBuffer, GL_STATIC_DRAW);
		}
		if (glGetError()) return -14;

		glClearColor((float)((bgColor >> 16) & 0xff) / 255.0f, (float)((bgColor >> 8) & 0xff) / 255.0f, (float)(bgColor & 0xff) / 255.0f, 1.0f);
	}

	//According to the docs, glTexImage2D initially expects images to be aligned on 4-byte
	//boundaries, but for ANDROID_BITMAP_FORMAT_RGB_565, AndroidBitmap_lockPixels aligns images
	//on 2-byte boundaries, making a few images look terrible!
	glPixelStorei(GL_UNPACK_ALIGNMENT, 2);

	glDisable(GL_DEPTH_TEST);
	glDisable(GL_CULL_FACE);
	glDisable(GL_DITHER);
	glDisable(GL_SCISSOR_TEST);
	glDisable(GL_STENCIL_TEST);
	if (type == TYPE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE_VR) {
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE);
		glBlendEquation(GL_FUNC_ADD);
	} else {
		glDisable(GL_BLEND);
	}
	glEnable(GL_TEXTURE_2D);
	glGetError(); //clear any eventual error flags
	
	glTex[0] = 0;
	glTex[1] = 0;

	switch (type) {
	case TYPE_LIQUID:
	case TYPE_SPIN:
	case TYPE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE:
		glGenTextures(1, glTex);
		if (glGetError() || !glTex[0]) return -15;
		break;
	default:
		glGenTextures(2, glTex);
		if (glGetError() || !glTex[0] || !glTex[1]) return -15;
		break;
	}

	if (type != TYPE_SPIN) {
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, glTex[0]);
		if (glGetError()) return -16;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		if (type == TYPE_LIQUID) {
			//create a default blue background
			((unsigned short*)floatBuffer)[0] = 0x31fb;
			((unsigned short*)floatBuffer)[1] = 0x5b3a;
			((unsigned short*)floatBuffer)[2] = 0x041f;
			((unsigned short*)floatBuffer)[3] = 0x34df;
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 2, 2, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (unsigned char*)floatBuffer);
		} else if (type == TYPE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE) {
			GLSoundParticle::fillTexture();
		} else if (type == TYPE_IMMERSIVE_PARTICLE_VR) {
			GLSoundParticle::fillTexture();
			//create a default blue background for the second texture
            glBindTexture(GL_TEXTURE_EXTERNAL_OES, glTex[1]);
			if (glGetError()) return -16;
			glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glOESTexture = glTex[1];
		} else {
			memset(floatBuffer, 0, 256);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)floatBuffer);
		}
		if (glGetError()) return -17;

		if (type != TYPE_LIQUID && type != TYPE_PARTICLE && type != TYPE_IMMERSIVE_PARTICLE && type != TYPE_IMMERSIVE_PARTICLE_VR) {
			glActiveTexture(GL_TEXTURE1);
			glBindTexture(GL_TEXTURE_2D, glTex[1]);
			if (glGetError()) return -18;
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			if (glGetError()) return -19;
		}

		//leave everything prepared for fast drawing :)
		glActiveTexture(GL_TEXTURE0);
	} else {
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, glTex[0]);
		if (glGetError()) return -16;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		//according to these:
		//http://stackoverflow.com/questions/5705753/android-opengl-es-loading-a-non-power-of-2-texture
		//http://stackoverflow.com/questions/3740077/can-opengl-es-render-textures-of-non-base-2-dimensions
		//https://www.khronos.org/opengles/sdk/docs/man/xhtml/glTexParameter.xhtml
		//non-power-of-2 textures cannot be used with GL_TEXTURE_WRAP_x other than GL_CLAMP_TO_EDGE
		//(even though it works on many devices, the spec says it shouldn't...)
		int TEXTURE_SIZE = computeSpinSize(estimatedWidth, estimatedHeight, dp1OrLess);
		TEXTURE_SIZE = ((TEXTURE_SIZE <= 16) ? 16 : 32);
		const float TEXTURE_COEF = ((TEXTURE_SIZE == 16) ? (255.0f * 0.078125f) : (255.0f * 0.0390625f));
		//generate the texture
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			float yf = (float)(y - (TEXTURE_SIZE >> 1));
			yf *= yf;
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				//0.0390625 = 1/25.6 (used for 32x32)
				//0.0625 = 1/16 (used for 20x20)
				//0.078125 = 1/12.8 (used for 16x16)
				float xf = (float)(x - (TEXTURE_SIZE >> 1));
				int v = (int)(TEXTURE_COEF * sqrtf((xf * xf) + yf));
				((unsigned char*)floatBuffer)[(y * TEXTURE_SIZE) + x] = ((v >= 255) ? 255 : (unsigned char)v);
			}
		}
		glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, TEXTURE_SIZE, TEXTURE_SIZE, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)floatBuffer);
		if (glGetError()) return -17;
	}

	glUseProgram(glProgram);
	if (glGetError()) return -20;

	switch (type) {
	case TYPE_LIQUID:
	case TYPE_SPIN:
		glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
		glTime = glGetUniformLocation(glProgram, "time");
		glAmplitude = glGetUniformLocation(glProgram, "amplitude");
		break;
	case TYPE_IMMERSIVE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE_VR:
		glMatrix = glGetUniformLocation(glProgram, "mvpMat");
	case TYPE_PARTICLE:
		glAmplitude = glGetUniformLocation(glProgram, "amplitude");
		glPos = glGetUniformLocation(glProgram, "posArr");
		glColor = glGetUniformLocation(glProgram, "colorArr");
		glBaseX = glGetUniformLocation(glProgram, "baseX");
		glTheta = glGetUniformLocation(glProgram, "thetaArr");
		glUniform1i(glGetUniformLocation(glProgram, "texColor"), 0);
		break;
	default:
		if (glAmplitude)
			glUniform1i(glGetUniformLocation(glProgram, "texAmplitude"), 0);
		else
			glAmplitude = glGetUniformLocation(glProgram, "amplitude");
		glUniform1i(glGetUniformLocation(glProgram, "texColor"), 1);
		break;
	}
	if (glGetError()) return -21;

	if (type == TYPE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE || type == TYPE_IMMERSIVE_PARTICLE_VR) {
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(2);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glVertexAttribPointer(2, 1, GL_FLOAT, false, 0, 0);

		if (type == TYPE_IMMERSIVE_PARTICLE_VR) {
			glEnableVertexAttribArray(3);
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
			glVertexAttribPointer(3, 4, GL_FLOAT, false, 0, 0);

			glEnableVertexAttribArray(4);
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[4]);
			glVertexAttribPointer(4, 2, GL_FLOAT, false, 0, 0);
		}
	} else if (type != TYPE_SPIN) {
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glVertexAttribPointer(0, 1, GL_FLOAT, false, 0, 0);
	} else {
		glEnableVertexAttribArray(0);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
	}
	if (type == TYPE_LIQUID) {
		glUseProgram(glProgram2);
		if (glGetError()) return -22;

		glUniform1i(glGetUniformLocation(glProgram2, "texColor"), 0);
		if (glGetError()) return -23;

		glEnableVertexAttribArray(1);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
		glVertexAttribPointer(1, 1, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(2);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[2]);
		glVertexAttribPointer(2, 4, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(3);
		glBindBuffer(GL_ARRAY_BUFFER, glBuf[3]);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, 0, 0);
	}
	if (glGetError()) return -24;

	commonColorIndex = ((type != TYPE_SPECTRUM) ? 4 : 0);
	commonColorIndexApplied = 4; //to control the result of (commonColorIndexApplied != commonColorIndex) inside glDrawFrame
	commonUpdateMultiplier(env, clazz, 0);

	glReleaseShaderCompiler();

	return 0;
}

void JNICALL glOnSurfaceChanged(JNIEnv* env, jclass clazz, int width, int height, int rotation, int cameraPreviewW, int cameraPreviewH, int dp1OrLess) {
	glViewport(0, 0, width, height);
	if (glProgram && glBuf[0] && glBuf[1] && width > 0 && height > 0) {
		if (glType == TYPE_SPIN) {
			int size = computeSpinSize(width, height, dp1OrLess);
			glVerticesPerRow = ((width + (size - 1)) / size) + 1;
			glRows = ((height + (size - 1)) / size);
			struct _coord {
				float x, y, z;
			};
			_coord* vertices = new _coord[(glVerticesPerRow << 1) * glRows];

			//compute the position of each vertex on the screen, respecting their order:
			// 1  3  5  7
			//             ...
			// 0  2  4  6
			//inPosition stores the distance of the vertex to the origin in the z component
			_coord* v = vertices;
			float y0 = 1.0f;
			for (int j = 0; j < glRows; j++) {
				//compute x and y every time to improve precision
				float y1 = 1.0f - ((float)((size << 1) * (j + 1)) / (float)height);
				for (int i = 0; i < glVerticesPerRow; i++) {
					float x = -1.0f + ((float)((size << 1) * i) / (float)width);
					v[(i << 1)    ].x = x;
					v[(i << 1)    ].y = y1;
					v[(i << 1) + 1].x = x;
					v[(i << 1) + 1].y = y0;

					x = (-x + 1.0f) * 0.5f;
					x = x * x;

					float y = (y0 + 1.0f) * 0.5f;
					float z = 1.0f - (sqrtf(x + (y * y)) / 1.25f);
					v[(i << 1) + 1].z = ((z > 0.0f) ? z : 0.0f);

					y = (y1 + 1.0f) * 0.5f;
					z = 1.0f - (sqrtf(x + (y * y)) / 1.25f);
					v[(i << 1)    ].z = ((z > 0.0f) ? z : 0.0f);
				}
				y0 = y1;
				v += (glVerticesPerRow << 1);
			}
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[0]);
			glBufferData(GL_ARRAY_BUFFER, (glVerticesPerRow << 1) * glRows * 3 * sizeof(float), vertices, GL_STATIC_DRAW);

			//inTexCoord stores the angle of the vertex in the z component
			v = vertices;
			for (int j = 0; j < glRows; j++) {
				for (int i = 0; i < glVerticesPerRow; i++) {
					int idx = (i << 1) + 1;
					v[idx].z = atan2f((v[idx].y + 1.0f) * 0.5f, (-v[idx].x + 1.0f) * 0.5f);
					v[idx].x = (float)i;
					v[idx].y = (float)j;
					//even vertices are located below odd ones
					idx--;
					v[idx].z = atan2f((v[idx].y + 1.0f) * 0.5f, (-v[idx].x + 1.0f) * 0.5f);
					v[idx].x = (float)i;
					v[idx].y = (float)(j + 1);
				}
				v += (glVerticesPerRow << 1);
			}
			glBindBuffer(GL_ARRAY_BUFFER, glBuf[1]);
			glBufferData(GL_ARRAY_BUFFER, (glVerticesPerRow << 1) * glRows * 3 * sizeof(float), vertices, GL_STATIC_DRAW);

			delete vertices;

			glVerticesPerRow <<= 1;
		} else if (glType == TYPE_PARTICLE || glType == TYPE_IMMERSIVE_PARTICLE || glType == TYPE_IMMERSIVE_PARTICLE_VR) {
			if (glSoundParticle)
				glSoundParticle->setAspect(width, height, rotation);
			if (width > height)
				glUniform2f(glGetUniformLocation(glProgram, "aspect"), (float)height / (float)width, 1.0f);
			else
				glUniform2f(glGetUniformLocation(glProgram, "aspect"), 1.0f, (float)width / (float)height);
			if (glType == TYPE_IMMERSIVE_PARTICLE_VR && glBuf[4] && cameraPreviewW > 0 && cameraPreviewH > 0) {
				glBindBuffer(GL_ARRAY_BUFFER, glBuf[4]);
				const float viewRatio = (float)width / (float)height;
				const float cameraRatio = (float)cameraPreviewW / (float)cameraPreviewH;
				float ratioError = viewRatio - cameraRatio;
				*((int*)&ratioError) &= 0x7fffffff; //abs ;)
				if (ratioError <= 0.01f) {
					glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), glTexCoordsRect, GL_STATIC_DRAW);
				} else {
					//is the camera ratio is too different from the view ratio, compensate for that
					//difference using the texture coords
					//(THIS ALGORITHM WAS A BIT SIMPLIFIED BECAUSE WE KNOW THAT
					//cameraPreviewW < width AND cameraPreviewH < height)
					float leftTex, topTex, rightTex = (float)width, bottomTex = (float)height;
					if (cameraRatio < viewRatio) {
						//crop top and bottom
						const float newH = bottomTex * ((float)cameraPreviewW / rightTex);
						const float diff = (((float)cameraPreviewH - newH) * 0.5f) / (float)cameraPreviewH;
						leftTex = 0.0f;
						topTex = diff;
						rightTex = 1.0f;
						bottomTex = 1.0f - diff;
					} else {
						//crop left and right
						const float newW = rightTex * ((float)cameraPreviewH / bottomTex);
						const float diff = (((float)cameraPreviewW - newW) * 0.5f) / (float)cameraPreviewW;
						leftTex = diff;
						topTex = 0.0f;
						rightTex = 1.0f - diff;
						bottomTex = 1.0f;
					}
					float texCoordsRect[] = {
						leftTex, bottomTex,
						rightTex, bottomTex,
						leftTex, topTex,
						rightTex, topTex
					};
					glBufferData(GL_ARRAY_BUFFER, (4 * 2) * sizeof(float), texCoordsRect, GL_STATIC_DRAW);
				}
			}
		}
	} else {
		glVerticesPerRow = 0;
		glRows = 0;
	}
}

int JNICALL glLoadBitmapFromJava(JNIEnv* env, jclass clazz, jobject bitmap) {
	AndroidBitmapInfo inf;
	if (AndroidBitmap_getInfo(env, bitmap, &inf))
		return ERR_INFO;

	if (inf.format != ANDROID_BITMAP_FORMAT_RGB_565)
		return ERR_FORMAT;

	unsigned char *dst = 0;
	if (AndroidBitmap_lockPixels(env, bitmap, (void**)&dst))
		return ERR_LOCK;

	if (!dst) {
		AndroidBitmap_unlockPixels(env, bitmap);
		return ERR_NOMEM;
	}

	glGetError(); //clear any eventual error flags
	glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, inf.width, inf.height, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, dst);
	int error = glGetError();

	AndroidBitmap_unlockPixels(env, bitmap);

	//to make (commonColorIndexApplied != commonColorIndex) become false inside glDrawFrame
	commonColorIndex = 4;
	commonColorIndexApplied = 4;

	return (error ? ERR_GL : 0);
}

void glSumData() {
	int i, idx, last;
	unsigned char avg, *processedData = (unsigned char*)(floatBuffer + 512);

#define MAX(A,B) (((A) > (B)) ? (A) : (B))
	//instead of dividing by 255, we are dividing by 256 (* 0.00390625f)
	//since the difference is visually unnoticeable
	idx = glAmplitude;
	for (i = 0; i < 6; i++)
		glUniform1f(idx++, (float)processedData[i] * 0.00390625f);
	for (; i < 20; i += 2)
		glUniform1f(idx++, (float)MAX(processedData[i], processedData[i + 1]) * 0.00390625f);
	for (; i < 36; i += 4) {
		avg = MAX(processedData[i], processedData[i + 1]);
		avg = MAX(avg, processedData[i + 2]);
		avg = MAX(avg, processedData[i + 3]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
	for (last = 44; last <= 100; last += 8) {
		avg = processedData[i++];
		for (; i < last; i++)
			avg = MAX(avg, processedData[i]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
	for (last = 116; last <= 228; last += 16) {
		avg = processedData[i++];
		for (; i < last; i++)
			avg = MAX(avg, processedData[i]);
		glUniform1f(idx++, (float)avg * 0.00390625f);
	}
#undef MAX
}

void JNICALL glDrawFrame(JNIEnv* env, jclass clazz) {
	if (commonColorIndexApplied != commonColorIndex) {
		commonColorIndexApplied = commonColorIndex;
		glActiveTexture(GL_TEXTURE1);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 256, 1, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, (unsigned char*)(COLORS + commonColorIndex));
		glActiveTexture(GL_TEXTURE0);
	}

	switch (glType) {
	case TYPE_IMMERSIVE_PARTICLE_VR:
		glUseProgram(glProgram2);
		glDisable(GL_BLEND);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
		glFlush(); //faster than glFinish :)

		glUseProgram(glProgram);
		glEnable(GL_BLEND);
		if (glSoundParticle)
			glSoundParticle->draw();
		break;
	case TYPE_PARTICLE:
	case TYPE_IMMERSIVE_PARTICLE:
		if (glSoundParticle) {
			glClear(GL_COLOR_BUFFER_BIT);
			glSoundParticle->draw();
		}
		break;
	case TYPE_LIQUID:
		glUseProgram(glProgram2);
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
		glFlush(); //faster than glFinish :)

		glUseProgram(glProgram);
		glUniform1f(glTime, (float)commonTime * 0.001f);
		glSumData();
		glDrawArrays(GL_TRIANGLE_STRIP, 0, 512 * 2);
		break;
	case TYPE_SPIN:
		if (glRows) {
			glUniform1f(glTime, (float)commonTime * 0.001f);

			glSumData();

			int i, first = 0;
			for (i = 0; i < glRows; i++) {
				glDrawArrays(GL_TRIANGLE_STRIP, first, glVerticesPerRow);
				first += glVerticesPerRow;
			}
		}
		break;
	default:
		glClear(GL_COLOR_BUFFER_BIT);
		if (glTime) {
			glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, 256, 1, 0, GL_ALPHA, GL_UNSIGNED_BYTE, (unsigned char*)(floatBuffer + 512));
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 256 * 2);
		} else {
#define MAX(A,B) (((A) > (B)) ? (A) : (B))
			//instead of dividing by 255, we are dividing by 256 (* 0.00390625f)
			//since the difference is visually unnoticeable
			int i, idx = glAmplitude, last;
			unsigned char avg, *processedData = (unsigned char*)(floatBuffer + 512);
			for (i = 0; i < 36; i++)
				glUniform1f(idx++, (float)processedData[i] * 0.00390625f);
			for (; i < 184; i += 2)
				glUniform1f(idx++, (float)MAX(processedData[i], processedData[i + 1]) * 0.00390625f);
			for (; i < 252; i += 4) {
				avg = MAX(processedData[i], processedData[i + 1]);
				avg = MAX(avg, processedData[i + 2]);
				avg = MAX(avg, processedData[i + 3]);
				glUniform1f(idx++, (float)avg * 0.00390625f);
			}
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 128 * 2);
#undef MAX
		}
		break;
	}
}

void JNICALL glOnSensorReset(JNIEnv* env, jclass clazz) {
	if (glSoundParticle)
		glSoundParticle->onSensorReset();
}

void JNICALL glOnSensorData(JNIEnv* env, jclass clazz, uint64_t sensorTimestamp, int sensorType, jfloatArray jvalues) {
	if (!glSoundParticle || !jvalues)
		return;
	float* values = (float*)env->GetPrimitiveArrayCritical(jvalues, 0);
	float v[] = { values[0], values[1], values[2] };
	env->ReleasePrimitiveArrayCritical(jvalues, values, JNI_ABORT);
	glSoundParticle->onSensorData(sensorTimestamp, sensorType, v);
}

void JNICALL glSetImmersiveCfg(JNIEnv* env, jclass clazz, int diffusion, int riseSpeed) {
	if (!glSoundParticle || (glType != TYPE_IMMERSIVE_PARTICLE && glType != TYPE_IMMERSIVE_PARTICLE_VR))
		return;
	glSoundParticle->setImmersiveCfg(diffusion, riseSpeed);
}

void JNICALL glReleaseView(JNIEnv* env, jclass clazz) {
	if (glSoundParticle) {
		delete glSoundParticle;
		glSoundParticle = 0;
	}
}
