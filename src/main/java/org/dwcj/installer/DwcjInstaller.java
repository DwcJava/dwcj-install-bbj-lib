package org.dwcj.installer;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.basis.api.admin.BBjAdminAppDeploymentApplication;
import com.basis.api.admin.BBjAdminAppDeploymentConfiguration;
import com.basis.api.admin.BBjAdminBase;
import com.basis.api.admin.BBjAdminException;
import com.basis.api.admin.BBjAdminFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.xml.sax.SAXException;




/**
 * perform the installation of a DWCJ app based on its JAR.
 */
public class DwcjInstaller {

  //buffer size
  public static final int BUFFER = 2048;
  public static final String TARGET_DEPENDENCY = "target/dependency/";
  public static final String POM_XML = "pom.xml";

  //a central StringBuilder instance to obtain the log
  private StringBuilder out = new StringBuilder();


  /**
   * extracz the POM file from the jar.
   *
   * @param zipFile the project's jar
   * @throws IOException  when io fails.
   */
  private void unzipPom(String zipFile) throws IOException {

    out.append("dwcj-installer: extracting pom.xml\n");

    File file = new File(zipFile);
    ZipFile zip = new ZipFile(file);
    Path path = Paths.get(zipFile);
    String directory = path.getParent().toString();
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {

      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();

      String currentEntry = entry.getName();
      if (!currentEntry.endsWith(POM_XML)) {
        continue;
      }

      File destFile = new File(directory, POM_XML);
      BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry));
      int currentByte;
      // establish buffer for writing file
      byte[] data = new byte[BUFFER];

      // write the current file to disk
      FileOutputStream fos = new FileOutputStream(destFile);
      BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
      // read and write until last byte is encountered
      while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
        dest.write(data, 0, currentByte);
      }
      dest.flush();
      dest.close();
      is.close();
    }
  }

  /**
   * extract the BBj progs from the JAR (they would be in /bbj inside).
   *
   * @param zipFile   the JAR file.
   * @param directory the directors where they should go.
   * @throws IOException  an exception with IO.
   */
  private void unzipBbjProgs(String zipFile, String directory) throws IOException {

    File file = new File(zipFile);
    ZipFile zip = new ZipFile(file);
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {
      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();

      String currentEntry = entry.getName();
      if (!currentEntry.endsWith(".bbj")) {
        continue;
      }

      File destFile = new File(directory, entry.getName());
      out.append("dwcj-installer: extracting " + destFile.getAbsolutePath() + "\n");
      Files.deleteIfExists(destFile.toPath());
      Paths.get(destFile.getPath()).getParent().toFile().mkdirs();

      BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry));
      int currentByte;

      // establish buffer for writing file
      byte[] data = new byte[BUFFER];

      // write the current file to disk
      FileOutputStream fos = new FileOutputStream(destFile);
      BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);

      // read and write until last byte is encountered
      while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
        dest.write(data, 0, currentByte);
      }
      dest.flush();
      dest.close();
      is.close();
    }
  }

  private Set<String> getDwcjDeps(String dir) {
    Pattern pattern = Pattern.compile("dwcj-*");
    return Stream.of(new File(dir).listFiles())
        .filter(file -> !file.isDirectory())
        .map(File::getName)
        .filter(pattern.asPredicate())
        .collect(Collectors.toSet());
  }


  boolean deleteDirectory(File directoryToBeDeleted) {
    File[] allContents = directoryToBeDeleted.listFiles();
    if (allContents != null) {
      for (File file : allContents) {
        deleteDirectory(file);
      }
    }
    return directoryToBeDeleted.delete();
  }

  /**
   * installs the DWCJ project based on its JAR file.
   *
   * @param sourceFilePath the path of the JAR file - it's considered a temporary location.
   *                       The file will be removed from here.
   * @param jarFileName the name of the file.
   * @param bbxdir the home directory of BBj.
   * @param deployroot The directory to which the deployment should be unpacked and installed.
   * @return The log of the activities.
   * @throws MavenInvocationException if there is a problem.
   * @throws IOException if there is a problem.
   * @throws BBjAdminException if there is a problem.
   * @throws ParserConfigurationException  if there is a problem.
   * @throws SAXException if there is a problem.
   * @throws NoSuchAlgorithmException if there is a problem.
   */
  public String install(
      String sourceFilePath,
      String jarFileName,
      String bbxdir,
      String deployroot)
      throws MavenInvocationException, IOException, BBjAdminException,
      ParserConfigurationException, SAXException, NoSuchAlgorithmException {

    String basedir = deployroot + jarFileName.substring(0, jarFileName.indexOf(".")) + "/";

    new File(basedir).mkdirs();

    String zipFilePath = basedir + jarFileName;
    Files.copy(Path.of(sourceFilePath), Path.of(zipFilePath), REPLACE_EXISTING);

    String pomFile = basedir + POM_XML;


    unzipPom(zipFilePath);
    File depdir = new File(basedir + TARGET_DEPENDENCY);
    if (depdir.exists()) {
      deleteDirectory(depdir);
    }

    InvocationRequest request = new DefaultInvocationRequest();
    request.setPomFile(new File(pomFile));
    request.setGoals(Collections.singletonList("dependency:copy-dependencies"));
    Invoker invoker = new DefaultInvoker();
    invoker.setOutputHandler(new MavenOutputHandler(out));
    invoker.setErrorHandler(new MavenOutputHandler(out));

    String mvn = MavenBinaryInstaller.getMavenBinary(deployroot);
    invoker.setMavenHome(new File(mvn));

    invoker.execute(request);

    //String engine = getEngine("/Users/beff/mvntesting/target/dependency/");
    //System.out.println(engine.toString());

    Set<String> deps = getDwcjDeps(basedir + TARGET_DEPENDENCY);
    Iterator<String> it = deps.iterator();
    while (it.hasNext()) {
      unzipBbjProgs(basedir + TARGET_DEPENDENCY + it.next(), basedir);
    }

    PomParser pomParser = new PomParser(pomFile);
    String appname = pomParser.getConfiguration("publishname");

    if (appname == null) {
      appname = jarFileName.substring(0, jarFileName.indexOf("."));
    }
    String user = pomParser.getConfiguration("username");
    if (user == null) {
      user = "admin";
    }
    String password = pomParser.getConfiguration("password");
    if (password == null) {
      password = "admin123";
    }
    String token = pomParser.getConfiguration("token");

    BBjAdminBase api;
    if (token != null) {
      api = BBjAdminFactory.getBBjAdmin(token);
    } else {
      api = BBjAdminFactory.getBBjAdmin(user, password);
    }


    //create BBj classpath
    ArrayList<String> cpEntries = new ArrayList<>();
    cpEntries.add("(bbj_default)");
    cpEntries.add(basedir + "target/dependency/*");
    cpEntries.add(zipFilePath);

    String apphandle = "dwcj_" + appname.toLowerCase();
    api.setClasspath(apphandle, cpEntries);
    api.commit();

    //create DWC app entry
    BBjAdminAppDeploymentConfiguration config = api.getRemoteConfiguration();
    // see https://documentation.basis.com/BASISHelp/WebHelp/javadocs/com/basis/api/admin/BBjAdminAppDeploymentApplication.html
    /*
        BBjAdminList<BBjAdminAppDeploymentResource> rlist = config.getResources();
        Iterator<BBjAdminAppDeploymentResource> itx = rlist.iterator();
        Boolean found=false;
        BBjAdminAppDeploymentResource iconResource=null;
        while (itx.hasNext()) {
            iconResource = itx.next();
            if (iconResource.getString(BBjAdminAppDeploymentResource.SOURCE_FILE_NAME)
               .equals(icon)) {
                found = true;
                break;
            }
        }

        if (!found) {
            iconResource = config.createResource();
            iconResource.setString(BBjAdminAppDeploymentResource.SOURCE_FILE_NAME, icon);
            config.getResources().add(iconResource);
            config.commit();
        }
    */

    BBjAdminAppDeploymentApplication newApp = config.createApplication();
    newApp.setString(BBjAdminAppDeploymentApplication.NAME, appname);
    newApp.setString(BBjAdminAppDeploymentApplication.PROGRAM, basedir + "bbj/dwcj.bbj");
    newApp.setString(BBjAdminAppDeploymentApplication.CLASSPATH, apphandle);
    // newApp.setString(BBjAdminAppDeploymentApplication.CONFIG_FILE, "/path/to/config.bbx")
    newApp.setString(BBjAdminAppDeploymentApplication.WORKING_DIRECTORY, basedir + "bbj/");
    newApp.setBoolean(BBjAdminAppDeploymentApplication.EXE_ENABLED, false);
    newApp.setBoolean(BBjAdminAppDeploymentApplication.BUI_ENABLED, false);
    newApp.setBoolean(BBjAdminAppDeploymentApplication.DWC_ENABLED, true);


    Boolean debug = pomParser.getConfiguration("debug") != null
        && pomParser.getConfiguration("debug").equals("true");
    if (Boolean.TRUE.equals(debug)) {
      newApp.getArguments().add("DEBUG");
    }

    String classname = pomParser.getConfiguration("classname");
    if (classname != null && !classname.isEmpty()) {
      newApp.getArguments().add("class=" + classname);
    }

    /* // icon
    newApp.setString(BBjAdminAppDeploymentApplication.SHORTCUT_ICON_RESOURCE_ID
       iconResource.getString(BBjAdminAppDeploymentResource.RESOURCE_ID));
    */

    newApp.commit();
    return out.toString();
  }
}
