package org.dwcj.installer;

import java.io.IOException;
import org.apache.maven.shared.invoker.InvocationOutputHandler;

/**
 * This class redirects the output of the maven-invoker to a
 * StringBuilder instance´ that is maintained by its creator.
 */
public class MavenOutputHandler implements InvocationOutputHandler {

  private final StringBuilder out;

  MavenOutputHandler(StringBuilder sb) {
    this.out = sb;
  }

  /**
   * Consumes a line of log.
   *
   * @param line is the line of log received by the invoker.
   * @throws IOException when there is a problem with consuming the line.
   */
  @Override
  public void consumeLine(String line) throws IOException {
    out.append("dwcj-installer: " + line + "\n");
  }
}
