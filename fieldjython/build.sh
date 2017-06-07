field=$1

root=`dirname $0`
cd $root

function join() {
    local IFS=$1
    shift
    echo "$*"
}

echo -- using javac from : --
echo `which javac` / `javac -fullversion`

echo -- using Field2 from : --


echo -- removing build directory --

rm -r build
mkdir build
cd build


echo

echo -- building main classes --
find ../src -iname '*.java' > source
find ../osx/src -iname '*.java' >> source

echo javac -Xlint:-deprecation -Xlint:-unchecked -XDignore.symbol.file -classpath "$field/build/field_agent.jar:$field/build/field_linker.jar:$field/build/classes/:../lib/jars/*:../osx/lib/jars/*"  @source -d .

javac -Xlint:-deprecation -Xlint:-unchecked -XDignore.symbol.file -classpath "$field/build/field_agent.jar:$field/build/field_linker.jar:$field/build/classes/:../lib/jars/*:../osx/lib/jars/*"  @source -d .

echo -- build complete --