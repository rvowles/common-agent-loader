package com.bluetrainsoftware.agent;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.spi.AttachProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.tools.attach.BsdVirtualMachine;
import sun.tools.attach.LinuxVirtualMachine;
import sun.tools.attach.SolarisVirtualMachine;
import sun.tools.attach.WindowsVirtualMachine;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

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

            if (!loaded.contains(fullName)) {
              if (fullName.startsWith("/") && isWindows()) {
                fullName = fullName.substring(1);
              }

              loadAgent(fullName, params);

              loaded.add(fullName);
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

  private static boolean isWindows() {
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
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (UnsatisfiedLinkError e) {
      throw new IllegalStateException("Native library for Attach API not available in this JRE", e);
    }

    return null;
  }

}
