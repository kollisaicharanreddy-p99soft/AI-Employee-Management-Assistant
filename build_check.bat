@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
set PATH=%JAVA_HOME%\bin;%PATH%
C:\Users\saicharan.kolli\Downloads\apache-maven-3.9.16\bin\mvn.cmd ^
  clean test ^
  -f c:\EmployeeManagement\pom.xml ^
  > c:\EmployeeManagement\build_out.txt 2>&1
echo EXIT_CODE=%ERRORLEVEL% >> c:\EmployeeManagement\build_out.txt
