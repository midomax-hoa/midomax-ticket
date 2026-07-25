@echo off
SET "JAVA_HOME=C:\Users\LENOVO\AppData\Local\Programs\Java\jdk-17.0.19+10"
SET "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo === Building Spring Boot JAR ===
call mvnw.cmd clean package -DskipTests
echo.
echo === Build DONE ===
