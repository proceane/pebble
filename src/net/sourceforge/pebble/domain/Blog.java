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
package net.sourceforge.pebble.domain;

import net.sourceforge.pebble.Constants;
import net.sourceforge.pebble.PluginProperties;
import net.sourceforge.pebble.comparator.BlogEntryByTitleComparator;
import net.sourceforge.pebble.dao.BlogEntryDAO;
import net.sourceforge.pebble.dao.CategoryDAO;
import net.sourceforge.pebble.dao.DAOFactory;
import net.sourceforge.pebble.dao.PersistenceException;
import net.sourceforge.pebble.event.DefaultEventDispatcher;
import net.sourceforge.pebble.event.EventDispatcher;
import net.sourceforge.pebble.event.EventListenerList;
import net.sourceforge.pebble.event.blog.BlogEvent;
import net.sourceforge.pebble.event.blog.BlogListener;
import net.sourceforge.pebble.event.blogentry.BlogEntryEvent;
import net.sourceforge.pebble.event.blogentry.BlogEntryListener;
import net.sourceforge.pebble.event.comment.CommentListener;
import net.sourceforge.pebble.event.trackback.TrackBackListener;
import net.sourceforge.pebble.logging.AbstractLogger;
import net.sourceforge.pebble.logging.CombinedLogFormatLogger;
import net.sourceforge.pebble.plugin.decorator.BlogEntryDecoratorManager;
import net.sourceforge.pebble.plugin.permalink.PermalinkProvider;
import net.sourceforge.pebble.plugin.permalink.DefaultPermalinkProvider;
import net.sourceforge.pebble.search.BlogIndexer;
import org.apache.lucene.index.IndexReader;

import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.*;

public class Blog extends AbstractBlog {

  public static final String EMAIL_KEY = "email";
  public static final String SMTP_HOST_KEY = "smtpHost";
  public static final String UPDATE_NOTIFICATION_PINGS_KEY = "updateNotificationPings";
  public static final String BLOG_OWNERS_KEY = "blogOwners";
  public static final String BLOG_CONTRIBUTORS_KEY = "blogContributors";
  public static final String PRIVATE_KEY = "private";
  public static final String LUCENE_ANALYZER_KEY = "luceneAnalyzer";
  public static final String BLOG_ENTRY_DECORATORS_KEY = "blogEntryDecorators";
  public static final String BLOG_LISTENERS_KEY = "blogListeners";
  public static final String BLOG_ENTRY_LISTENERS_KEY = "blogEntryListeners";
  public static final String COMMENT_LISTENERS_KEY = "commentListeners";
  public static final String TRACKBACK_LISTENERS_KEY = "trackBackListeners";
  public static final String EVENT_DISPATCHER_KEY = "eventDispatcher";
  public static final String LOGGER_KEY = "logger";
  public static final String PERMALINK_PROVIDER_KEY = "permalinkProviderName";

  /** the ID of this blog */
  private String id = "default";

  /** the collection of YearlyBlog instance that this root blog is managing */
  private List yearlyBlogs;

  /** the response manager associated with this blog */
  private ResponseManager responseManager;

  /** the root category associated with this blog */
  private Category rootCategory;

  /** the collection of tags associated with this blog */
  private Map tags;

  /** the referer filter associated with this blog */
  private RefererFilterManager refererFilterManager;

  /** the story index associated with this blog */
  private StaticPageIndex staticPageIndex;

  /** the editable theme belonging to this blog */
  private Theme editableTheme;

  /** the permalink provider in use */
  private PermalinkProvider permalinkProvider;

  /** the log used to log referers, requests, etc */
  private AbstractLogger logger;

  /** the decorator manager associated with this blog */
  private BlogEntryDecoratorManager blogEntryDecoratorManager;

  /** the event dispatcher */
  private EventDispatcher eventDispatcher;

  /** the event listener list */
  private EventListenerList eventListenerList;

  /** the plugin properties */
  private PluginProperties pluginProperties;

  /**
   * Creates a new Blog instance, based at the specified location.
   *
   * @param root    an absolute path pointing to the root directory of the blog
   */
  public Blog(String root) {
    super(root);
  }

  protected void init() {
    super.init();

    try {
      Class c = Class.forName(getPermalinkProviderName());
      setPermalinkProvider((PermalinkProvider)c.newInstance());
    } catch (Exception e) {
      e.printStackTrace();
      setPermalinkProvider(new DefaultPermalinkProvider());
    }
    // initialise tags and categories
    tags = new TreeMap();
    try {
      DAOFactory factory = DAOFactory.getConfiguredFactory();
      CategoryDAO dao = factory.getCategoryDAO();
      rootCategory = dao.getCategories(this);
    } catch (PersistenceException pe) {
      pe.printStackTrace();
    }

    responseManager = new ResponseManager(this);
    refererFilterManager = new RefererFilterManager(this);
    pluginProperties = new PluginProperties(this);
    blogEntryDecoratorManager = new BlogEntryDecoratorManager(this, getBlogEntryDecorators());

    try {
      DAOFactory factory = DAOFactory.getConfiguredFactory();
      BlogEntryDAO dao = factory.getBlogEntryDAO();
      yearlyBlogs = dao.getYearlyBlogs(this);
    } catch (PersistenceException pe) {
      pe.printStackTrace();
    }

    staticPageIndex = new StaticPageIndex(this);

    File imagesDirectory = new File(getImagesDirectory());
    if (!imagesDirectory.exists()) {
      imagesDirectory.mkdir();
    }

    File filesDirectory = new File(getFilesDirectory());
    if (!filesDirectory.exists()) {
      filesDirectory.mkdir();
    }

    File logDirectory = new File(getLogsDirectory());
    if (!logDirectory.exists()) {
      logDirectory.mkdir();
    }

    initLogger();
    initEventDispatcher();
    initBlogListeners();
    initBlogEntryListeners();
    initCommentListeners();
    initTrackBackListeners();
  }

  /**
   * Initialises the logger for this blog.
   */
  private void initLogger() {
    log.info("Initializing logger");

    try {
      Class c = Class.forName(getLoggerName());
      Constructor cons = c.getConstructor(new Class[] {Blog.class});
      this.logger = (AbstractLogger)cons.newInstance(new Object[] {this});
    } catch (Exception e) {
      e.printStackTrace();
      this.logger = new CombinedLogFormatLogger(this);
    }
  }

  /**
   * Initialises the event dispatcher for this blog.
   */
  private void initEventDispatcher() {
    log.info("Initializing event dispatcher");
    eventListenerList = new EventListenerList();

    try {
      Class c = Class.forName(getEventDispatcherName());
      this.eventDispatcher = (EventDispatcher)c.newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      this.eventDispatcher = new DefaultEventDispatcher();
    }

    eventDispatcher.setEventListenerList(eventListenerList);
  }

  /**
   * Initialises any blog listeners configured for this blog.
   */
  private void initBlogListeners() {
    log.info("Registering blog listeners");

    String classNames = getBlogListeners();
    if (classNames != null && classNames.length() > 0) {
      String classes[] = classNames.split("\\s+");
      for (int i = 0; i < classes.length; i++) {
        if (!classes[i].startsWith("#")) {
          try {
            Class c = Class.forName(classes[i].trim());
            BlogListener listener = (BlogListener)c.newInstance();
            eventListenerList.addBlogListener(listener);
          } catch (Exception e) {
            log.error("Blog listener " + classes[i] + " could not be registered", e);
          }
        }
      }
    }
  }

  /**
   * Initialises any blog entry listeners configured for this blog.
   */
  private void initBlogEntryListeners() {
    log.info("Registering blog entry listeners");

    String classNames = getBlogEntryListeners();
    if (classNames != null && classNames.length() > 0) {
      String classes[] = classNames.split("\\s+");
      for (int i = 0; i < classes.length; i++) {
        if (!classes[i].startsWith("#")) {
          try {
            Class c = Class.forName(classes[i].trim());
            BlogEntryListener listener = (BlogEntryListener)c.newInstance();
            eventListenerList.addBlogEntryListener(listener);
          } catch (Exception e) {
            log.error("Blog entry listener " + classes[i] + " could not be registered", e);
          }
        }
      }
    }

    eventListenerList.addBlogEntryListener(new IndexBlogEntryListener());
  }

  /**
   * Initialises any comment listeners configured for this blog.
   */
  private void initCommentListeners() {
    log.info("Registering comment listeners");

    String classNames = getCommentListeners();
    if (classNames != null && classNames.length() > 0) {
      String classes[] = classNames.split("\\s+");
      for (int i = 0; i < classes.length; i++) {
        if (!classes[i].startsWith("#")) {
          try {
            Class c = Class.forName(classes[i].trim());
            CommentListener listener = (CommentListener)c.newInstance();
            eventListenerList.addCommentListener(listener);
          } catch (Exception e) {
            log.error("Comment listener " + classes[i] + " could not be registered", e);
          }
        }
      }
    }

    // register the response manager so that the recent list of comments
    // can be maintained
    eventListenerList.addCommentListener(responseManager);
  }

  /**
   * Initialises any TrackBack listeners configured for this blog.
   */
  private void initTrackBackListeners() {
    log.info("Registering TrackBack listeners");

    String classNames = getTrackBackListeners();
    if (classNames != null && classNames.length() > 0) {
      String classes[] = classNames.split("\\s+");
      for (int i = 0; i < classes.length; i++) {
        if (!classes[i].startsWith("#")) {
          try {
            Class c = Class.forName(classes[i].trim());
            TrackBackListener listener = (TrackBackListener)c.newInstance();
            eventListenerList.addTrackBackListener(listener);
          } catch (Exception e) {
            log.error("TrackBack listener " + classes[i] + " could not be registered", e);
          }
        }
      }
    }

    // register the response manager so that the recent list of TrackBacks
    // can be maintained
    eventListenerList.addTrackBackListener(responseManager);
  }

  /**
   * Utility method to load all blog entries. This is called so that recent
   * comments and TrackBacks can be registered and displayed on the home page.
   */
  private void loadAllBlogEntries() {
    Thread t = new Thread("pebble/" + getId() + "/preload") {
      public void run() {
        for (int year = yearlyBlogs.size()-1; year >= 0; year--) {
          YearlyBlog yearlyBlog = (YearlyBlog)yearlyBlogs.get(year);
          MonthlyBlog[] months = yearlyBlog.getMonthlyBlogs();
          for (int month = 11; month >= 0; month--) {
            months[month].getAllDailyBlogs();
          }
        }

        recalculateTagRankings();
      }
    };

    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  /**
   * Gets the default properties for a Blog.
   *
   * @return    a Properties instance
   */
  protected Properties getDefaultProperties() {
    Properties defaultProperties = new Properties();
    defaultProperties.setProperty(NAME_KEY, "My blog");
    defaultProperties.setProperty(DESCRIPTION_KEY, "");
    defaultProperties.setProperty(IMAGE_KEY, "");
    defaultProperties.setProperty(AUTHOR_KEY, "Blog Owner");
    defaultProperties.setProperty(EMAIL_KEY, "blog@yourdomain.com");
    defaultProperties.setProperty(TIMEZONE_KEY, "Europe/London");
    defaultProperties.setProperty(LANGUAGE_KEY, "en");
    defaultProperties.setProperty(COUNTRY_KEY, "GB");
    defaultProperties.setProperty(CHARACTER_ENCODING_KEY, "UTF-8");
    defaultProperties.setProperty(RECENT_BLOG_ENTRIES_ON_HOME_PAGE_KEY, "3");
    defaultProperties.setProperty(RECENT_COMMENTS_ON_HOME_PAGE_KEY, "3");
    defaultProperties.setProperty(UPDATE_NOTIFICATION_PINGS_KEY, "");
    defaultProperties.setProperty(THEME_KEY, "default");
    defaultProperties.setProperty(PRIVATE_KEY, FALSE);
    defaultProperties.setProperty(LUCENE_ANALYZER_KEY, "org.apache.lucene.analysis.standard.StandardAnalyzer");
    defaultProperties.setProperty(BLOG_ENTRY_DECORATORS_KEY, "net.sourceforge.pebble.plugin.decorator.HideUnapprovedBlogEntriesDecorator\r\nnet.sourceforge.pebble.plugin.decorator.HideUnapprovedResponsesDecorator\r\nnet.sourceforge.pebble.plugin.decorator.HtmlDecorator\r\nnet.sourceforge.pebble.plugin.decorator.EscapeMarkupDecorator\r\nnet.sourceforge.pebble.plugin.decorator.RelativeUriDecorator\r\nnet.sourceforge.pebble.plugin.decorator.BlogTagsDecorator");
    defaultProperties.setProperty(PERMALINK_PROVIDER_KEY, "net.sourceforge.pebble.plugin.permalink.DefaultPermalinkProvider");
    defaultProperties.setProperty(EVENT_DISPATCHER_KEY, "net.sourceforge.pebble.event.DefaultEventDispatcher");
    defaultProperties.setProperty(LOGGER_KEY, "net.sourceforge.pebble.logging.CombinedLogFormatLogger");

    return defaultProperties;
  }

  /**
   * Gets the ID of this blog.
   *
   * @return  the ID as a String
   */
  public String getId() {
    return this.id;
  }

  /**
   * Sets the ID of this blog.
   *
   * @param id    the ID as a String
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Gets the e-mail address of the blog owner.
   *
   * @return    the e-mail address
   */
  public String getEmail() {
    return properties.getProperty(EMAIL_KEY);
  }

  /**
   * Gets a Collection of e-mail addresses.
   *
   * @return  a Collection of String instances
   */
  public Collection getEmailAddresses() {
    return Arrays.asList(getEmail().split(","));
  }

  /**
   * Gets the first of multiple e-mail addresses.
   *
   * @return  the firt e-mail address as a String
   */
  public String getFirstEmailAddress() {
    Collection emailAddresses = getEmailAddresses();
    if (emailAddresses != null && !emailAddresses.isEmpty()) {
      return (String)emailAddresses.iterator().next();
    } else {
      return "";
    }
  }

  /**
   * Gets the name of the SMTP (mail) host.
   *
   * @return    the SMTP host
   */
  public String getSmtpHost() {
    return properties.getProperty(SMTP_HOST_KEY);
  }

  /**
   * Gets the list of websites that should be pinged when this
   * blog is updated.
   *
   * @return  a comma separated list of website addresses
   */
  public String getUpdateNotificationPings() {
    return properties.getProperty(UPDATE_NOTIFICATION_PINGS_KEY);
  }

  /**
   * Gets the collection of websites that should be pinged when this
   * blog is updated.
   *
   * @return  a Collection of Strings representing website addresses
   */
  public Collection getUpdateNotificationPingsAsCollection() {
    Set websites = new HashSet();
    String notificationPings = getUpdateNotificationPings();
    if (notificationPings != null && notificationPings.length() > 0) {
      String s[] = notificationPings.split("\\s+");
      for (int i = 0; i < s.length; i++) {
        websites.add(s[i]);
      }
    }

    return websites;
  }

  /**
   * Gets a comma separated list of the users that are blog owners
   * for this blog.
   *
   * @return  a String containng a comma separated list of user names
   */
  public String getBlogOwners() {
    return properties.getProperty(BLOG_OWNERS_KEY);
  }

  /**
   * Gets a comma separated list of the users that are blog contributors
   * for this blog.
   *
   * @return  a String containng a comma separated list of user names
   */
  public String getBlogContributors() {
    return properties.getProperty(BLOG_CONTRIBUTORS_KEY);
  }

  /**
   * Gets the name of the Lucene analyzer to use.
   *
   * @return  a fully qualified class name
   */
  public String getLuceneAnalyzer() {
    return properties.getProperty(LUCENE_ANALYZER_KEY);
  }

  /**
   * Gets the name of the logger in use.
   *
   * @return  a fully qualified class name
   */
  public String getLoggerName() {
    return properties.getProperty(LOGGER_KEY);
  }

  /**
   * Gets a Collection containing the names of users that are blog owners
   * for this blog.
   *
   * @return  a Collection containng user names as Strings
   */
  public Collection getUsersInRole(String roleName) {
    Set users = new HashSet();
    String commaSeparatedUsers = null;
    StringTokenizer tok = null;

    if (roleName.equals(Constants.BLOG_OWNER_ROLE)) {
      commaSeparatedUsers = properties.getProperty(BLOG_OWNERS_KEY);
    } else if (roleName.equals(Constants.BLOG_CONTRIBUTOR_ROLE)) {
      commaSeparatedUsers = properties.getProperty(BLOG_CONTRIBUTORS_KEY);
    }

    if (commaSeparatedUsers != null) {
      tok = new StringTokenizer(commaSeparatedUsers, ",");
      while (tok.hasMoreTokens()) {
        users.add(tok.nextToken().trim());
      }
    }

    return users;
  }

  /**
   * Determines whether the specified user is in the specified role.
   *
   * @param roleName    the name of the role
   * @param user        the name of the user
   * @return  true if the user is a member of the role or the list of users
   *          is empty, false otherwise
   */
  public boolean isUserInRole(String roleName, String user) {
    Collection users = getUsersInRole(roleName);
    if (users.isEmpty() || users.contains(user)) {
      return true;
    }

    return false;
  }

  /**
   * Gets the YearlyBlog instance for the specified year.
   *
   * @param year    the year as an int (e.g. 2003)
   * @return    a YearlyBlog instance
   */
  public YearlyBlog getBlogForYear(int year) {
    Iterator it = yearlyBlogs.iterator();
    YearlyBlog yearlyBlog = null;
    while (it.hasNext()) {
      yearlyBlog = (YearlyBlog)it.next();
      if (yearlyBlog.getYear() == year) {
        return yearlyBlog;
      }
    }

    yearlyBlog = new YearlyBlog(this, year);
    if (year > getBlogForToday().getMonthlyBlog().getYearlyBlog().getYear()) {
      yearlyBlogs.add(yearlyBlog);
      Collections.sort(yearlyBlogs);
    }

    return yearlyBlog;
  }

  /**
   * Gets the YearlyBlog instance representing this year.
   *
   * @return  a YearlyBlog instance for this year
   */
  public YearlyBlog getBlogForThisYear() {
    Calendar cal = getCalendar();
    return getBlogForYear(cal.get(Calendar.YEAR));
  }

  /**
   * Gets all YearlyBlogs managed by this root blog.
   *
   * @return  a Collection of YearlyBlog instances
   */
  public List getYearlyBlogs() {
    return yearlyBlogs;
  }

  /**
   * Gets the MonthlyBlog instance representing the first month that
   * contains blog entries.
   *
   * @return  a MonthlyBlog instance
   */
  public MonthlyBlog getBlogForFirstMonth() {
    YearlyBlog year;

    if (!yearlyBlogs.isEmpty()) {
      year = (YearlyBlog)yearlyBlogs.get(0);
    } else {
      year = getBlogForYear(getCalendar().get(Calendar.YEAR));
    }

    for (int i = 1; i <= 12; i++) {
      MonthlyBlog month = year.getBlogForMonth(i);
      if (month.hasBlogEntries()) {
        return month;
      }
    }

    return year.getBlogForFirstMonth();
  }

  /**
   * Gets a DailyBlog intance for the specified Date.
   *
   * @param date    a java.util.Date instance
   * @return    a DailyBlog instance representing the specified Date
   */
  public DailyBlog getBlogForDay(Date date) {
    Calendar cal = getCalendar();
    cal.setTime(date);

    int year = cal.get(Calendar.YEAR);
    int month = (cal.get(Calendar.MONTH) + 1);
    int day = cal.get(Calendar.DAY_OF_MONTH);

    return getBlogForDay(year, month, day);
  }

  /**
   * Gets the DailyBlog instance for today.
   *
   * @return    a DailyBlog instance
   */
  public DailyBlog getBlogForToday() {
    return this.getBlogForDay(getCalendar().getTime());
  }

  /**
   * Gets a DailyBlog intance for the specified year, month and day.
   *
   * @param year    the year as an int
   * @param month   the month as an int
   * @param day     the day as an int
   * @return    a DailyBlog instance representing the specified year, month and day
   */
  public DailyBlog getBlogForDay(int year, int month, int day) {
    return getBlogForMonth(year, month).getBlogForDay(day);
  }

  /**
   * Gets a MonthlyBlog intance for the specified year and month.
   *
   * @param year    the year as an int
   * @param month   the month as an int
   * @return    a MonthlyBlog instance representing the specified year and month
   */
  public MonthlyBlog getBlogForMonth(int year, int month) {
    return getBlogForYear(year).getBlogForMonth(month);
  }

  /**
   * Gets the MonthlyBlog instance representing this month.
   *
   * @return  a MonthlyBlog instance for this month
   */
  public MonthlyBlog getBlogForThisMonth() {
    Calendar cal = getCalendar();
    return getBlogForMonth(cal.get(Calendar.YEAR), (cal.get(Calendar.MONTH) + 1));
  }

  /**
   * Given a YearlyBlog, this method returns the YearlyBlog instance
   * representing the previous year.
   *
   * @param yearlyBlog    a YearlyBlog instance
   * @return    a YearlyBlog representing the previous year
   */
  public YearlyBlog getBlogForPreviousYear(YearlyBlog yearlyBlog) {
    return getBlogForYear(yearlyBlog.getYear() - 1);
  }

  /**
   * Given a YearlyBlog, this method returns the YearlyBlog instance
   * representing the next year.
   *
   * @param yearlyBlog    a YearlyBlog instance
   * @return    a YearlyBlog representing the next year
   */
  public YearlyBlog getBlogForNextYear(YearlyBlog yearlyBlog) {
    return getBlogForYear(yearlyBlog.getYear() + 1);
  }

  /**
   * Gets all blog entries for this blog.
   *
   * @return  a List of BlogEntry objects
   */
  public List getBlogEntries() {
    List blogEntries = new ArrayList();
    for (int year = yearlyBlogs.size()-1; year >= 0; year--) {
      YearlyBlog yearlyBlog = (YearlyBlog)yearlyBlogs.get(year);
      MonthlyBlog[] months = yearlyBlog.getMonthlyBlogs();
      for (int month = 11; month >= 0; month--) {
        blogEntries.addAll(months[month].getBlogEntries());
      }
    }

    return blogEntries;
  }

  /**
   * Gets the number of blog entries for this blog.
   *
   * @return  an int
   */
  public int getNumberOfBlogEntries() {
    int count = 0;
    for (int year = yearlyBlogs.size()-1; year >= 0; year--) {
      YearlyBlog yearlyBlog = (YearlyBlog)yearlyBlogs.get(year);
      MonthlyBlog[] months = yearlyBlog.getMonthlyBlogs();
      for (int month = 11; month >= 0; month--) {
        count += months[month].getNumberOfBlogEntries();
      }
    }

    return count;
  }

  /**
   * Determines whether there are blog entries for the specified day.
   *
   * @param day         a DailyBlog instance
   * @return  true if there are entries, false otherwise
   */
  public boolean hasEntries(DailyBlog day) {
    return day.hasEntries();
  }

  /**
   * Gets the most recent blog entries, the number
   * of which is specified.
   *
   * @param numberOfEntries the number of entries to get
   * @param approvedOnly    true if only approved should be included, false otherwise
   * @return a List containing the most recent blog entries
   */
  public List getRecentBlogEntries(int numberOfEntries, boolean approvedOnly) {
    return getRecentBlogEntries((Category)null, numberOfEntries, approvedOnly);
  }

  /**
   * Gets the most recent blog entries for a given category, the number
   * of which is specified.
   *
   * @param   category          a Category instance, or null
   * @param   numberOfEntries   the number of entries to get
   * @param   approvedOnly      true if only approved should be included, false otherwise
   * @return  a List containing the most recent blog entries
   */
  public List getRecentBlogEntries(Category category, int numberOfEntries, boolean approvedOnly) {
    DailyBlog firstDayOfBlog = getBlogForFirstMonth().getBlogForDay(1);
    Calendar cal = getCalendar();
    List entries = new ArrayList();

    while (entries.size() < numberOfEntries) {
      DailyBlog day = getBlogForDay(cal.getTime());

      if (day.hasEntries()) {
        Iterator it = new ArrayList(day.getEntries(category)).iterator();
        while ((entries.size() < numberOfEntries) && it.hasNext()) {
          BlogEntry blogEntry = (BlogEntry)it.next();
          if (!approvedOnly || (approvedOnly && blogEntry.isApproved())) {
            entries.add(blogEntry);
          }
        }
      }

      if (day == firstDayOfBlog) {
        break;
      } else {
        cal.add(Calendar.DAY_OF_YEAR, -1);
      }
    }

    return entries;
  }

  /**
   * Gets the most recent blog entries for a given tag, the number
   * of which is specified.
   *
   * @param tag             a String
   * @param numberOfEntries the number of entries to get
   * @param approvedOnly    true if only approved should be included, false otherwise
   * @return a List containing the most recent blog entries
   */
  public List getRecentBlogEntries(String tag, int numberOfEntries, boolean approvedOnly) {
    DailyBlog firstDayOfBlog = getBlogForFirstMonth().getBlogForDay(1);
    Calendar cal = getCalendar();

    List entries = new ArrayList();
    while ((entries.size() < numberOfEntries)) {
      DailyBlog day = getBlogForDay(cal.getTime());
      if (day.hasEntries()) {
        Iterator it = new ArrayList(day.getEntries(tag)).iterator();
        while ((entries.size() < numberOfEntries) && it.hasNext()) {
          BlogEntry blogEntry = (BlogEntry)it.next();
          if (!approvedOnly || (approvedOnly && blogEntry.isApproved())) {
            entries.add(blogEntry);
          }
        }
      }

      if (day == firstDayOfBlog) {
        break;
      } else {
        cal.add(Calendar.DAY_OF_YEAR, -1);
      }
    }

    return entries;
  }

  /**
   * Gets the date that this blog was last updated.
   *
   * @return  a Date instance representing the time of the most recent entry
   */
  public Date getLastModified() {
    Date date = new Date(0);
    List blogEntries = getRecentBlogEntries();
    Iterator it = blogEntries.iterator();
    while (it.hasNext()) {
      BlogEntry blogEntry = (BlogEntry)it.next();
      if (blogEntry.getDate().after(date)) {
        date = blogEntry.getDate();
      }
    }

    return date;
  }

  /**
   * Gets the blog entry with the specified id.
   *
   * @param entryId   the id of the blog entry
   * @return  a BlogEntry instance, or null if the entry couldn't be found
   */
  public BlogEntry getBlogEntry(String entryId) {
    if (entryId == null || entryId.length() == 0) {
      return null;
    }

    Calendar cal = getCalendar();
    cal.setTime(new Date(Long.parseLong(entryId)));

    int year = cal.get(Calendar.YEAR);
    int month = (cal.get(Calendar.MONTH) + 1);
    int day = cal.get(Calendar.DAY_OF_MONTH);

    DailyBlog blog = getBlogForDay(year, month, day);
    return blog.getEntry(entryId);
  }

  public BlogEntry getPreviousBlogEntry(BlogEntry blogEntry) {
    BlogEntry previous = blogEntry.getDailyBlog().getPreviousBlogEntry(blogEntry);
    if (previous != null) {
      return previous;
    }

    DailyBlog dailyBlog = blogEntry.getDailyBlog();
    DailyBlog firstDailyBlog = getBlogForFirstMonth().getBlogForFirstDay();
    while (dailyBlog != firstDailyBlog && previous == null) {
      dailyBlog = dailyBlog.getPreviousDay();
      previous = dailyBlog.getLastBlogEntry();
    }

    return previous;
  }

  public BlogEntry getNextBlogEntry(BlogEntry blogEntry) {
    BlogEntry next = blogEntry.getDailyBlog().getNextBlogEntry(blogEntry);
    if (next != null) {
      return next;
    }

    DailyBlog dailyBlog = blogEntry.getDailyBlog();
    DailyBlog lastDailyBlog = getBlogForToday();
    while (dailyBlog != lastDailyBlog && next == null) {
      dailyBlog = dailyBlog.getNextDay();
      next = dailyBlog.getFirstBlogEntry();
    }

    return next;
  }

  /**
   * Gets the object managing responses.
   *
   * @return  a ResponseManager instance
   */
  public ResponseManager getResponseManager() {
    return this.responseManager;
  }

  /**
   * Gets the categories associated with this blog.
   *
   * @return  a List of Category instances
   */
  public List getCategories() {
    CategoryBuilder builder = new CategoryBuilder(this, rootCategory);
    return builder.getCategories();
  }

  /**
   * Gets a specific category.
   *
   * @return  a Category instance
   */
  public Category getCategory(String id) {
    CategoryBuilder builder = new CategoryBuilder(this, rootCategory);
    return builder.getCategory(id);
  }

  /**
   * Gets the root category for this blog.
   *
   * @return  a Category instance
   */
  public Category getRootCategory() {
    return this.rootCategory;
  }

  /**
   * Sets the root category for this blog.
   *
   * @param category    a Category instance
   */
  public void setRootCategory(Category category) {
    this.rootCategory = category;
  }

  /**
   * Adds a category.
   *
   * @param category    the Category to be added
   */
  public synchronized void addCategory(Category category) {
    if (getCategory(category.getId()) == null) {
      CategoryBuilder builder = new CategoryBuilder(this, rootCategory);
      builder.addCategory(category);
    }
  }

  /**
   * Removes a category.
   *
   * @param category    the Category to be removed
   */
  public synchronized void removeCategory(Category category) {
    if (getCategory(category.getId()) != null) {
      CategoryBuilder builder = new CategoryBuilder(this, rootCategory);
      builder.removeCategory(category);
    }
  }

  /**
   * Gets the list of tags associated with this blog.
   */
  public List getTags() {
    List<Tag> list = new ArrayList<Tag>(tags.values());
    Iterator it = list.iterator();
    while (it.hasNext()) {
      Tag tag = (Tag)it.next();
      if (tag.getNumberOfBlogEntries() == 0) {
        it.remove();
      }
    }

    return list;
  }


  /**
   * Gets the tag with the specified name.
   *
   * @param name    the name as a String
   * @return  a Tag instance
   */
  public Tag getTag(String name) {
    name = Tag.encode(name);
    Tag tag = (Tag)tags.get(name);
    if (tag == null) {
      tag = new Tag(name, this);
      tags.put(name, tag);
    }

    return tag;
  }

  synchronized void recalculateTagRankings() {
    if (tags.size() > 0) {
      // find the maximum
      int maxBlogEntries = 0;
      int total = 0;
      Iterator it = tags.values().iterator();
      while (it.hasNext()) {
        Tag tag = (Tag)it.next();
        total += tag.getNumberOfBlogEntries();
        if (tag.getNumberOfBlogEntries() > maxBlogEntries) {
          maxBlogEntries = tag.getNumberOfBlogEntries();
        }
      }

      // calculate the mean (to nearest int)
      float mean = total / tags.size();

      int[] thresholds = new int[10];
      thresholds[0] = (int)((1.0/6.0) * mean);
      thresholds[1] = (int)((2.0/6.0) * mean);
      thresholds[2] = (int)((3.0/6.0) * mean);
      thresholds[3] = (int)((4.0/6.0) * mean);
      thresholds[4] = (int)((5.0/6.0) * mean);
      thresholds[5] = (int)mean;
      thresholds[6] = (int)(mean + (1.0 * ((maxBlogEntries-mean)/4.0)));
      thresholds[7] = (int)(mean + (2.0 * ((maxBlogEntries-mean)/4.0)));
      thresholds[8] = (int)(mean + (3.0 * ((maxBlogEntries-mean)/4.0)));
      thresholds[9] = maxBlogEntries;

      // now rank the tags
      it = tags.values().iterator();
      while (it.hasNext()) {
        Tag tag = (Tag)it.next();
        tag.calculateRank(thresholds);
      }
    }
  }

  /**
   * Gets the object managing referer filters.
   *
   * @return  a RefererFilterManager instance
   */
  public RefererFilterManager getRefererFilterManager() {
    return this.refererFilterManager;
  }

  /**
   * Gets the story index.
   *
   * @return  a StaticPageIndex instance
   */
  public StaticPageIndex getStaticPageIndex() {
    return this.staticPageIndex;
  }

  /**
   * Logs this request for blog.
   *
   * @param request   the HttpServletRequest instance for this request
   */
  public synchronized void log(HttpServletRequest request) {
    logger.log(request);
  }

  /**
   * Gets an object representing the editable theme.
   *
   * @return    an EditableTheme instance
   */
  public Theme getEditableTheme() {
    return editableTheme;
  }

  /**
   * Sets an object representing the editable theme.
   *
   * @param editableTheme    an EditableTheme instance
   */
  public void setEditableTheme(Theme editableTheme) {
    this.editableTheme = editableTheme;
  }

  /**
   * Gets the location where the blog files are stored.
   *
   * @return    an absolute, local path on the filing system
   */
  public String getFilesDirectory() {
    return getRoot() + File.separator + "files";
  }

  /**
   * Gets the location where the blog theme is stored.
   *
   * @return    an absolute, local path on the filing system
   */
  public String getThemeDirectory() {
    return getRoot() + File.separator + "theme";
  }

  /**
   * Gets the location where the plugin properties file is stored.
   *
   * @return    an absolute, local path on the filing system
   */
  public String getPluginPropertiesFile() {
    return getRoot() + File.separator + "plugin.properties";
  }

  /**
   * Determines whether this blog is public.
   *
   * @return  true if public, false otherwise
   */
  public boolean isPublic() {
    return properties.getProperty(PRIVATE_KEY).equalsIgnoreCase(FALSE);
  }

  /**
   * Determines whether this blog is private.
   *
   * @return  true if public, false otherwise
   */
  public boolean isPrivate() {
    return properties.getProperty(PRIVATE_KEY).equalsIgnoreCase(TRUE);
  }

  public List getDraftBlogEntries() {
    List blogEntries = new ArrayList();

    try {
      DAOFactory factory = DAOFactory.getConfiguredFactory();
      BlogEntryDAO dao = factory.getBlogEntryDAO();
      blogEntries.addAll(dao.getDraftBlogEntries(this));
    } catch (PersistenceException e) {
      e.printStackTrace();
    }

    Collections.sort(blogEntries, new BlogEntryByTitleComparator());

    return blogEntries;
  }

  /**
   * Gets the list of blog entry templates for this blog.
   *
   * @return  a list of BlogEntry instances
   */
  public List getBlogEntryTemplates() {
    List blogEntries = new ArrayList();

    try {
      DAOFactory factory = DAOFactory.getConfiguredFactory();
      BlogEntryDAO dao = factory.getBlogEntryDAO();
      blogEntries.addAll(dao.getBlogEntryTemplates(this));
    } catch (PersistenceException e) {
      e.printStackTrace();
    }

    Collections.sort(blogEntries, new BlogEntryByTitleComparator());

    return blogEntries;
  }

  /**
   * Gets the list of static pages for this blog.
   *
   * @return  a list of BlogEntry instances
   */
  public List getStaticPages() {
    List blogEntries = new ArrayList();

    try {
      DAOFactory factory = DAOFactory.getConfiguredFactory();
      BlogEntryDAO dao = factory.getBlogEntryDAO();
      blogEntries.addAll(dao.getStaticPages(this));
    } catch (PersistenceException e) {
      e.printStackTrace();
    }

    Collections.sort(blogEntries, new BlogEntryByTitleComparator());

    return blogEntries;
  }

  /**
   * Gets the draft blog entry with the specified id.
   *
   * @param entryId   the id of the blog entry
   * @return  a BlogEntry instance, or null if the entry couldn't be found
   */
  public BlogEntry getDraftBlogEntry(String entryId) {
    if (entryId == null) {
      return null;
    }

    List blogEntries = getDraftBlogEntries();
    Iterator it = blogEntries.iterator();
    while (it.hasNext()) {
      BlogEntry blogEntry = (BlogEntry)it.next();
      if (blogEntry.getId().equals(entryId)) {
        return blogEntry;
      }
    }

    return null;
  }

  /**
   * Gets the blog entry template with the specified id.
   *
   * @param entryId   the id of the blog entry
   * @return  a BlogEntry instance, or null if the entry couldn't be found
   */
  public BlogEntry getBlogEntryTemplate(String entryId) {
    if (entryId == null) {
      return null;
    }

    List blogEntries = getBlogEntryTemplates();
    Iterator it = blogEntries.iterator();
    while (it.hasNext()) {
      BlogEntry blogEntry = (BlogEntry)it.next();
      if (blogEntry.getId().equals(entryId)) {
        return blogEntry;
      }
    }

    return null;
  }

  /**
   * Gets the static page with the specified id.
   *
   * @param entryId   the id of the blog entry
   * @return  a BlogEntry instance, or null if the entry couldn't be found
   */
  public BlogEntry getStaticPage(String entryId) {
    if (entryId == null) {
      return null;
    }

    List blogEntries = getStaticPages();
    Iterator it = blogEntries.iterator();
    while (it.hasNext()) {
      BlogEntry blogEntry = (BlogEntry)it.next();
      if (blogEntry.getId().equals(entryId)) {
        return blogEntry;
      }
    }

    return null;
  }

  /**
   * Called to start (i.e. activate/initialise, restore the theme, etc) this
   * blog.
   */
  void start() {
    // create an index if one doesn't already exist
    if (!IndexReader.indexExists(getIndexDirectory())) {
      BlogIndexer indexer = new BlogIndexer();
      indexer.index(this);
    }

    logger.start();
    editableTheme.restore();
    loadAllBlogEntries();

    // call blog listeners
    eventDispatcher.fireBlogEvent(new BlogEvent(this, BlogEvent.BLOG_STARTED));
  }

  /**
   * Called to shutdown this blog.
   */
  void stop() {
    logger.stop();
    logger = null;
    editableTheme.backup();

    // call blog listeners
    eventDispatcher.fireBlogEvent(new BlogEvent(this, BlogEvent.BLOG_STOPPED));
  }

  /**
   * Gets the logger associated with this blog.
   *
   * @return    an AbstractLogger implementation
   */
  public AbstractLogger getLogger() {
    return this.logger;
  }

  /**
   * Gets the list of plugins.
   *
   * @return  a comma separated list of class names
   */
  public String getBlogEntryDecorators() {
    return getProperty(BLOG_ENTRY_DECORATORS_KEY);
  }

  /**
   * Gets the decorator manager associated with this blog.
   *
   * @return  a BlogEntryDecoratorManager instance
   */
  public BlogEntryDecoratorManager getBlogEntryDecoratorManager() {
    return this.blogEntryDecoratorManager;
  }

  /**
   * Gets the list of blog listeners as a String.
   *
   * @return  a String
   */
  public String getBlogListeners() {
    return getProperty(BLOG_LISTENERS_KEY);
  }

  /**
   * Gets the list of blog entry listeners as a String.
   *
   * @return  a String
   */
  public String getBlogEntryListeners() {
    return getProperty(BLOG_ENTRY_LISTENERS_KEY);
  }

  /**
   * Gets the list of comment listeners as a String.
   *
   * @return  a String
   */
  public String getCommentListeners() {
    return getProperty(COMMENT_LISTENERS_KEY);
  }

  /**
   * Gets the list of TrackBack listeners as a String.
   *
   * @return  a String
   */
  public String getTrackBackListeners() {
    return getProperty(TRACKBACK_LISTENERS_KEY);
  }

  /**
   * Gets the name the event dispatcher.
   *
   * @return  a String
   */
  public String getEventDispatcherName() {
    return getProperty(EVENT_DISPATCHER_KEY);
  }

  /**
   * Gets the event dispatcher in use.
   *
   * @return  an EventDispatcher implementation
   */
  public EventDispatcher getEventDispatcher() {
    return this.eventDispatcher;
  }

  /**
   * Gets the event listsner list.
   */
  public EventListenerList getEventListenerList() {
    return this.eventListenerList;
  }

  public PluginProperties getPluginProperties() {
    return this.pluginProperties;
  }

  /**
   * Gets the name of the permalink provider.
   *
   * @return    the fully qualified class name of the permalink provider
   */
  public String getPermalinkProviderName() {
    return properties.getProperty(PERMALINK_PROVIDER_KEY);
  }

  /**
   * Gets the permalink provider in use.
   *
   * @return  a PermalinkProvider instance
   */
  public PermalinkProvider getPermalinkProvider() {
    return this.permalinkProvider;
  }

  /**
   * Sets the permalink provider in use.
   *
   * @param provider    PermalinkProvider instance
   */
  public void setPermalinkProvider(PermalinkProvider provider) {
    this.permalinkProvider = provider;
    this.permalinkProvider.setBlog(this);
  }

}