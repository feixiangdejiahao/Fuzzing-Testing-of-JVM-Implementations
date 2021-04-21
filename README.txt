run the program
javac -cp "src:lib/*" -d out src/ClassmingEntry.java
java -cp "out:lib/*:/Library/Java/JavaVirtualMachines/adoptopenjdk-8.jdk/Contents/Home/jre/lib/rt.jar" ClassmingEntry (MacOS)
java -cp "out:lib/*:/$JAVA_HOME/jre/lib/rt.jar" ClassmingEntry (Linux)

look up JVM versions
/usr/libexec/java_home -V(MacOS)
