# DuplexDelete

This program is available to the public domain, and as such is distributed wholly without any license or copyright claim. You are free to copy this code, modify it, use, distribute, and repackage it in any way you see fit. This project exists for purely educational purposes, in the hopes that it may be useful as a learning tool and a demonstration of basic proficiency in Java programming.

DuplexDelete is a simple experimental Java program which allows user to kick off a multi-threaded delete operation. Made by request for a friend who needed to quickly delete immense volumes of logging data on a server. Start by picking a folder to start in, and DuplexDelete scans the entire hierarchy from that point (may take some time). Time spent in the scan stage is saved several times over in reducing delete overhead. Overall time saved proved to be less than expected, but increased delete speeds by around 15-20%

***TO RUN:

This repository comes with a compiled JAR version of the program pre-packaged in /bin/ called DuplexDelete.jar. This program does not have any dependencies outside of a functional JRE, recommended version 7 or later (though older versions may also be compatible). To run, simply copy to any desired directory and open.

***TO COMPILE:

This program was written in Eclipse and should contain all the necessary infrastructure to be imported successfully into the workspace of any modern version of Eclipse.

To import into Eclipse, simply extract or clone the full contents of this repository to the desired directory, launch Eclipse, and then select File -> Import... and select "Existing Projects into Workspace". Then, under Select root directory, browse to the folder you copied this repo into and click Finish.

To compile, create a default run configuration pointing the main class to DuplexDelete. No other configuration should be necessary outside of specifying which project the run configuration is for. Then, go to File -> Export... and select Runnable JAR File. Pick your export destination (likely the bin folder of the project directory), select the "Package required libraries into generated JAR" option under Library Handling, and click Finish.

In theory, this program should also compile with the standard java CL compiler, however I have not tested this. Your mileage may vary.

Have fun!
