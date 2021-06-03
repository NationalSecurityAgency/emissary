# IDE integration

The poms are setup to use a different *project.build.directory* depending on the IDE you are using.  The
reason for this is so that running Maven on the command line and using your IDE will not interfere with each
other.  So keep in mind, your IDE will no longer build to the /target directory.

You can run/debug the emissary.Emissary class in your IDE.  Just setup the same arguments you would use
on the command line.  Running tests in your IDE is also supported.  IntelliJ and Netbeans will read the 
surefire plugin configuration and use it.  Eclipse does not read the surefire configuration, so the process
is explained below.  

### Setting up remote debugging in your IDE

For info on how to setup remote debugging in your IDE, reference the
following articles:

- For [IntelliJ](https://www.jetbrains.com/idea/help/run-debug-configuration-remote.html)
- For
[Eclipse](http://help.eclipse.org/kepler/index.jsp?topic=%2Forg.eclipse.jubula.client.ua.help%2Fhtml%2Freference%2Fnode47.html)

### IntelliJ (Preferred By Our Developers)

When [IntelliJ](https://www.jetbrains.com/idea/) runs a Maven project, a system property named
*idea.version* is set.  We use this property to activate the *intellij* profile in the 
 pom and change the *project.build.directory* to target-idea, which is a sibling to the target 
directory.  The `mvn clean` command will not remove this directory, but cleaning from IntelliJ should remove it.

Because we are using the jacoco plugin, we must put @argLine at the beginning of the surefire plugin
to allow jacoco to add what it needs.  See the second answer [here](http://stackoverflow.com/questions/23190107/cannot-use-jacoco-jvm-args-and-surefire-jvm-args-together-in-maven).
But that can cause IntelliJ to report this if you run a test

```
Error: Could not find or load main class @{argLine}
```

The fix is to tell IntelliJ not to use that.  Uncheck argLine from 
Preferences -> Build,Execution,Deployment -> Build Tools -> Maven -> Running Tests.
See [stackoverflow.com](http://stackoverflow.com/questions/24115142/intellij-error-when-running-unit-test-could-not-find-or-load-main-class-suref) 
for more info

### Eclipse

When [Eclipse](https://eclipse.org/) runs with the [M2E](http://www.eclipse.org/m2e/), a system property named
*m2e.version* is set.  We can use that fact to activate the *eclipse* profile in the pom and then change
the *project.build.directory* to target-eclipse.  I will be a sibling to the target directory.  The `mvn clean` command 
will not remove this directory, but cleaning from Eclipse should remove it.

The *eclipse* profile also configures some exclusions for the m2e plugin so it doesn't warn about not knowing how to 
handle some of the Maven plugins used.

When running tests in Eclipse, so you will need to set the environment variable PROJECT_BASE to the directory
eclipse is using to build the project.  So in the run configuration of a test, add the following to the 
Run Configuration -> Environment -> New

```
PROJECT_BASE = ${project_loc}/target-eclipse
```

Unfortunately you will have to do this for every test.


### Netbeans

Unfortunately, I could not find a system property [Netbeans](https://netbeans.org/) sets when running 
a Maven project, so we are unable to automatically activate the *netbeans* profile like we can with 
the *eclipse* and *intellij* profile.  Fortunately, it is easy to add a system property yourself.  
Open Netbean's Preferences -> Java -> Maven and then add `-Dnetbeans.ide` into the *Global Execution Options*.  
Afterwards, the *netbeans* profile will be activated in the pom and change 
the *project.build.directory* to target-netbeans, which is a sibling to the target directory.  

The `mvn clean` command will not remove this directory, but cleaning from Netbeans should remove it.