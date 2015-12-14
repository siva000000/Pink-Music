cd C:\Users\USAGEDESIGN\Downloads\Pink-Music\libs\armeabi\libSimpleVisualizerJni
arm-linux-androideabi-objdump -S -d SimpleVisualizerJni.o > armeabi.txt
move armeabi.txt C:\Users\USAGEDESIGN\Downloads\Pink-Music\jni\armeabi.txt

cd C:\Users\USAGEDESIGN\Downloads\Pink-Music\libs\armeabi-v7a\libSimpleVisualizerJni
arm-linux-androideabi-objdump -S -d SimpleVisualizerJni.o > armeabi-v7a.txt
move armeabi-v7a.txt C:\Users\USAGEDESIGN\Downloads\Pink-Music\jni\armeabi-v7a.txt

cd C:\Users\USAGEDESIGN\Downloads\Pink-Music\libs\x86\libSimpleVisualizerJni
objdump -S -d SimpleVisualizerJni.o > x86.txt
move x86.txtC:\Users\USAGEDESIGN\Downloads\Pink-Music\jni\x86.txt

pause
