@echo off
rem == Execute from the device
rem su
rem setprop service.adb.tcp.port 5555
rem stop adbd
rem start adbd

rem adb connect 10.100.32.206:5555
adb connect 192.168.0.13:5555
