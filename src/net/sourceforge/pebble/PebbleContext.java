/*
 * Copyright (c) 2003-2006, Simon Brown
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *   - Neither the name of Pebble nor the names of its contributors may
 *     be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.sourceforge.pebble;

import net.sourceforge.pebble.dao.DAOFactory;
import net.sourceforge.pebble.dao.file.FileDAOFactory;
import net.sourceforge.pebble.util.RelativeDate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

/**
 * A bean representing configurable properties for Pebble.
 *
 * @author    Simon Brown
 */
public class PebbleContext {

  /** the log used by this class */
  private static Log log = LogFactory.getLog(PebbleContext.class);

  private String buildVersion;
  private String buildDate;

  private String dataDirectory = "${user.home}/pebble";
  private String url;
  private long fileUploadSize = 2048;
  private long fileUploadQuota = -1;
  private DAOFactory daoFactory = new FileDAOFactory();

  private static final String BUILD_VERSION_KEY = "build.version";
  private static final String BUILD_DATE_KEY = "build.date";

  /** the time that Pebble was started */
  private Date startTime;

  public PebbleContext() {
    // and note when Pebble was started
    this.startTime = new Date();

    try {
      Properties buildProperties = new Properties();
      InputStream in = getClass().getClassLoader().getResourceAsStream("pebble-build.properties");
      if (in != null) {
        buildProperties.load(in);
        this.buildVersion = buildProperties.getProperty(BUILD_VERSION_KEY);
        this.buildDate = buildProperties.getProperty(BUILD_DATE_KEY);
        in.close();
      }
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      e.printStackTrace();
    }
  }

  /**
   * Gets the version of Pebble being used.
   *
   * @return    the version number as a String
   */
  public String getBuildVersion() {
    return this.buildVersion;
  }

  /**
   * Gets the date that Pebble was built.
   *
   * @return    the date as a String
   */
  public String getBuildDate() {
    return this.buildDate;
  }

  /**
   * Gets the amount of time that Pebble has been running for.
   *
   * @return  a number of milliseconds
   */
  public RelativeDate getUptime() {
    return new RelativeDate(new Date().getTime() - startTime.getTime());
  }

  public long getMemoryUsageInKB() {
    return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024;
  }

  public long getTotalMemoryInKB() {
    return Runtime.getRuntime().totalMemory() / 1024;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String s) {
    this.url = s;

    if (url != null && !(url.length() == 0) && !url.endsWith("/")) {
      url += "/";
    }
  }

  public long getFileUploadSize() {
    return fileUploadSize;
  }

  public void setFileUploadSize(long fileUploadSize) {
    this.fileUploadSize = fileUploadSize;
  }

  public long getFileUploadQuota() {
    return fileUploadQuota;
  }

  public void setFileUploadQuota(long fileUploadQuota) {
    this.fileUploadQuota = fileUploadQuota;
  }

  public DAOFactory getDaoFactory() {
    return daoFactory;
  }

  public void setDaoFactory(DAOFactory daoFactory) {
    this.daoFactory = daoFactory;
  }

  public String getDataDirectory() {
    return dataDirectory;
  }

  public void setDataDirectory(String dataDirectory) {
    this.dataDirectory = evaluateDirectory(dataDirectory);
  }

  /**
   * Replaces ${some.property} at the start of the string with the value
   * from System.getProperty(some.property).
   *
   * @param s   the String to transform
   * @return  a new String, or the same String if it doesn't start with a
   *          property name delimited by ${...}
   */
  private String evaluateDirectory(String s) {
    log.debug("Raw string is " + s);
    if (s.startsWith("${")) {
      int index = s.indexOf("}");
      String propertyName = s.substring(2, index);
      String propertyValue = System.getProperty(propertyName);
      log.debug(propertyName + " = " + propertyValue);
      return propertyValue + s.substring(index+1);
    } else {
      return s;
    }
  }



}