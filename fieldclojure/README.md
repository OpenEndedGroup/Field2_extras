# Field2/Clojure plugin

This plugin adds Clojure support to Field2

### Prerequisites 
A working Field2 build. All other dependencies are bundled into this repository.

### Build
`./build.sh path_to_field2_repository`

### Installation

In your `~/.field/plugins.edn` add something along the lines of:

`{ :classpath 
["/Users/marc/fieldwork2_extras/fieldclojure/build/" 
"/Users/marc/fieldwork2_extras/fieldclojure/lib/jars/*"] }`

Changing `/Users/marc/fieldwork2_extras` to the appropriate path. 
 