#!/usr/bin/env bash
set -u -o pipefail

if [ -z "${1-}" ]; then
    echo
    echo Usage: ./build.sh path_to_field2_repository
    echo
    exit
fi

field="$(cd "$(dirname "$1")"; pwd)/$(basename "$1")"

root=`dirname $0`
cd $root

function join() {
    local IFS=$1
    shift
    echo "$*"
}

echo -- using javac from : --
echo `which javac` / `javac -fullversion`
echo

echo -- using Field2 from : --
echo $field
echo


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