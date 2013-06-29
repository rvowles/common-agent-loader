common-agent-loader
===================

The common Agent loader is designed to ease the loading of Java Agents at runtime rather than from the command line.
Such agents can do pretty much everything a command line agent can do, but they tend to "get in early", which is why
class reloaders typically do this.

Class instrumenters however - like many JPA implementations (except that piece of crap, Hibernate) - are all good
for this technique.

The mechanisms uses some of the classes from the JDK when your code is running on a JRE - and this technique is taken
from JMockIt code.


Using common-agent-loader
-------------------------

import com.bluetrainsoftware.agent.AgentLoader;

...


    AgentLoader.loadAgent("file://.....", params)

for when you know the full file path. The params is optional if your instrumenter does not take parameters.

if you are using a URLClassLoader (which most web containers do), then you can instead use:

    AgentLoader.findAgentInClasspath(partial, params)

Again, the params is optional. For example - if you have an Instrumentation libraray called "flibble-agent" and
you are using 1.63, you can just use

    AgentLoader.findAgentInClasspath('flibble-agent', "debug=0")

(Assuming flibble-agent has a debug level). This will find it whether it is a file, or url or some other element that
makes sense to the URLClassLoader.

Compatibility
-------------
JDK 1.6 and above.

Licenses
--------
JDK stuff retains its GPL2v + Classpath Exception license. BTS stuff retains MIT license.





