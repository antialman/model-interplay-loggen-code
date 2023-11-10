# Multi-model log generator

Implementation of a multi-model log generator. Based on the multi-model monitor in https://github.com/antialman/model-interplay-monitoring-code


## Running the application

The application is currently compiled as a single [runnable fatjar](https://github.com/antialman/model-interplay-loggen-code/releases/tag/0.1.0) (requires installing [Java 11 JDK](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)).

It is possible to use either the graphical user interface or the command line interface.


### Graphical user interface

Graphical user interface allows to select the output event log, parameters for log generation, and input process models, as well as editing the violation cost of each model. The results can be visualised on a trace-by trace basis. The generated log is then replayed using the multi-model monitoring approach.

Command: `java -jar .\multi-model-loggen.jar`
* _If there are other versions of Java installed on the same computer then it may be necessary to use the command: `java -Djava.library.path=. -jar .\multi-model-loggen`_


### Command line interface

Command line interface generates an event log based on the input cost model file and additional log generation parameters.

Command: `java -jar .\multi-model-loggen.jar --cmd -c ..\input\scalability\test01_costModel.txt -o ..\output\test3.xes -p 50 -n 75 -e 5`
* `--cmd` - Speficies that the application should be started in the command line mode
* `-c` - Input model files and violation costs. Model and corresponding violation cost separated by comma. Each model on separate line. File extension defines the model type (decl - Declare; pnml - Data Petri Net; ltl - same format as decl file, but using raw formulas instead of Declare templates)
* `-o` - Output event log path
* `-p` - Number of positive traces to generate
* `-n` - Number of negative traces to generate
* `-e` - Probability of considering violating next states at each step. Default: 5


## Supported input models
* Declare models in .decl format (as produced by RuM: https://rulemining.org/)
* LTL formulas is custom format
  * Activities and attributes must be defined the same way as in .decl file
  * Each LTL formula on a separate line followed by `|` and optional data conditions for each activity (spearated by `|`)
  * _For example: (  <> ( A ) \\/ <>( B )  ) |A.x>5 and A.x<9 |B.y=4 or B.z<10_
* Data Petri Nets (as produced by ProM: https://www.promtools.org/doku.php?id=prom69)
  * Plugin "Create DPN (Text language based)" by F.Mannhardt 


## Limitations

* Parentheses are currently not supported in conditions.
* Only variable to constant comparisons (x>5, y<=3, z=="a" etc.) are supported.
* Implementation has been tested only on Windows 10.
* Silent transitions are not supported.
* The implementation may produce incorrect results if two transitions of a DPN, that can be enabled at the same time, have overlapping guards

## Authors

Conference paper (see the technical report [here](https://arxiv.org/abs/2111.13136)):
* Anti Alman
* Fabrizio Maria Maggi
* Marco Montali
* Fabio Patrizi
* Andrey Rivkin

Implementation: Anti Alman

