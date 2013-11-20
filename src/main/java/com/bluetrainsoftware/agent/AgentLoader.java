package com.bluetrainsoftware.agent;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.spi.AttachProvider;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.tools.attach.BsdVirtualMachine;
import sun.tools.attach.LinuxVirtualMachine;
import sun.tools.attach.SolarisVirtualMachine;
import sun.tools.attach.WindowsVirtualMachine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * This technique and some of the code is taken from JMockIt. It allows us to use agentmain Java Agents in a JRE.
 *
 * It also discovers the jar in the classpath and loads it from there if desired.
 *
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class AgentLoader {
  private static final Logger log = LoggerFactory.getLogger(AgentLoader.class);
  private static final List<String> loaded = new ArrayList<String>();

  private static String discoverPid() {
    String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
    int p = nameOfRunningVM.indexOf('@');

    return nameOfRunningVM.substring(0, p);
  }

  private static final AttachProvider ATTACH_PROVIDER = new AttachProvider() {
    @Override
    public String name() {
      return null;
    }

    @Override
    public String type() {
      return null;
    }

    @Override
    public VirtualMachine attachVirtualMachine(String id) {
      return null;
    }

    @Override
    public List<VirtualMachineDescriptor> listVirtualMachines() {
      return null;
    }
  };

  public static void loadAgent(String jarFilePath) {
    loadAgent(jarFilePath, "");
  }

  public static void loadAgent(String jarFilePath, String params) {
    log.info("dynamically loading javaagent for {}", jarFilePath);

    try {
      VirtualMachine vm;

      String pid = discoverPid();

      if (AttachProvider.providers().isEmpty()) {
        vm = getVirtualMachineImplementationFromEmbeddedOnes(pid);
      }
      else {
        vm = VirtualMachine.attach(pid);
      }

      vm.loadAgent(jarFilePath, params);
      vm.detach();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void findAgentInClasspath(String partial) {
    findAgentInClasspath(partial, "");
  }

  public static void findAgentInClasspath(String partial, String params) {
    try {
      if (AgentLoader.class.getClassLoader() instanceof URLClassLoader) {
        URLClassLoader cl = (URLClassLoader) (AgentLoader.class.getClassLoader());
        for (URL url : cl.getURLs()) {
          if (url.getFile().contains(partial)) {
            String fullName = url.toURI().getPath();

	          boolean embedded = false;

	          if (fullName == null&&  url.getProtocol().equals("jar") && url.getPath().contains("!/")) {
		          fullName = extractJar(url, partial);
		          embedded = true;
	          }

            if (fullName != null && !loaded.contains(fullName)) {
              if (fullName.startsWith("/") && isWindows()) {
                fullName = fullName.substring(1);
              }
	            try {
	              loadAgent(fullName, params);

                loaded.add(fullName);
	            } finally {
		            if (embedded) {
			            new File(fullName).delete();
		            }
	            }
            }

            return;
          }
        }
      }
    } catch (URISyntaxException use) {
      throw new RuntimeException(use);
    }

    log.error("Unable to find agent with partial: {}", partial);
  }

	/**
	 * This method takes the jar:file:path-to-filename.war!/WEB-INF/jar/jar-file/ offset that is included in the
	 * url classpath and extracts out a single jar containing the files in that match that url.
	 *
	 *
	 * @param path - full url entry in the classpath
	 * @param partial - the name of the partial we are trying to match
	 * @return It returns null if it fails or a full path to the jar file if it succeeds
	 */
	public static String extractJar(URL path, String partial) {
		String fullPath = null;

		String[] jarNames = path.getPath().split(":");

		if (jarNames.length >= 2) {
			String fileAndOffset = jarNames[1];
			int pos = fileAndOffset.indexOf('!');
			if (pos >= 0) {
				String file = fileAndOffset.substring(0, pos);
				String offset = fileAndOffset.substring(pos + 2);
				int offsetLength = offset.length();
//				log.debug("file {} offset {}", file, offset);

				fullPath = System.getProperty("java.io.tmpdir") + "/" + partial + ".jar";
//				log.debug("Outputting {} to {}", path.getPath(), fullPath);

				try {
					JarOutputStream outputJar = new JarOutputStream(new FileOutputStream(fullPath));

					JarFile inputZip = new JarFile(file);
					Enumeration<JarEntry> entries = inputZip.entries();

					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();

						if (entry.getName().startsWith(offset)) {
							String internalName = entry.getName().substring(offsetLength);
							JarEntry ze = new JarEntry(entry);
							Field f = ze.getClass().getSuperclass().getDeclaredField("name");
							f.setAccessible(true);
							f.set(ze, internalName);

//							log.debug("copying {} to {}", entry.getName(), internalName);

							outputJar.putNextEntry(ze);
							IOUtils.copy(inputZip.getInputStream(entry), outputJar);
							outputJar.closeEntry();
						}
					}

					outputJar.close();
					inputZip.close();

				} catch (Exception ex) {
					log.error("Failed to copy partial {}", partial, ex);

					fullPath = null;
				}
			}
		}

		return fullPath;
	}

  private static final boolean isWindows() {
    return File.separatorChar == '\\';
  }



  @SuppressWarnings("UseOfSunClasses")
  private static VirtualMachine getVirtualMachineImplementationFromEmbeddedOnes(String pid) {
    try {
      if (isWindows()) {
        return new WindowsVirtualMachine(ATTACH_PROVIDER, pid);
      }

      String osName = System.getProperty("os.name");

      if (osName.startsWith("Linux") || osName.startsWith("LINUX")) {
        return new LinuxVirtualMachine(ATTACH_PROVIDER, pid);
      } else if (osName.startsWith("Mac OS X")) {
        return new BsdVirtualMachine(ATTACH_PROVIDER, pid);
      } else if (osName.startsWith("Solaris")) {
        return new SolarisVirtualMachine(ATTACH_PROVIDER, pid);
      }
    } catch (AttachNotSupportedException e) {
      throw new RuntimeException(e);
    } catch(IOException e) {
	    throw new RuntimeException(e);
    } catch (UnsatisfiedLinkError e) {
      throw new IllegalStateException("Native library for Attach API not available in this JRE", e);
    }

    return null;
  }
//
//
//	public static void main(String[] args) throws Exception {
//		URL url = new URL("jar:file:/Users/richard/java/uoa/findathesis/war/target/findathesis-war-1.1-SNAPSHOT.war!/WEB-INF/jars/avaje-ebeanorm-agent-3.2.1/");
//
//		System.setProperty("java.io.tmpdir", "/tmp");
//
//		extractJar(url, "avaje-ebeanorm-agent");
//	}
}
