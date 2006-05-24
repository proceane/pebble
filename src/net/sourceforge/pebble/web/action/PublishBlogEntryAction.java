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
package net.sourceforge.pebble.web.action;

import net.sourceforge.pebble.Constants;
import net.sourceforge.pebble.domain.*;
import net.sourceforge.pebble.web.view.NotFoundView;
import net.sourceforge.pebble.web.view.RedirectView;
import net.sourceforge.pebble.web.view.View;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Allows the user to publish/unpublish a blog entry.
 *
 * @author    Simon Brown
 */
public class PublishBlogEntryAction extends SecureAction {

  /** the log used by this class */
  private static final Log log = LogFactory.getLog(PublishBlogEntryAction.class);

  /**
   * Peforms the processing associated with this action.
   *
   * @param request  the HttpServletRequest instance
   * @param response the HttpServletResponse instance
   * @return the name of the next view
   */
  public View process(HttpServletRequest request, HttpServletResponse response) throws ServletException {
    Blog blog = (Blog)getModel().get(Constants.BLOG_KEY);
    String id = request.getParameter("entry");
    String submit = request.getParameter("submit");
    String now = request.getParameter("now");

    BlogService service = new BlogService();
    BlogEntry blogEntry = service.getBlogEntry(blog, id);

    if (blogEntry == null) {
      return new NotFoundView();
    }

    if (submit.equals("Publish")) {
      DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, blog.getLocale());
      dateFormat.setTimeZone(blog.getTimeZone());
      dateFormat.setLenient(false);

      Date publishDate = new Date();
      if (now != null && now.equalsIgnoreCase("false")) {
        String dateAsString = request.getParameter("date");
        if (dateAsString != null && dateAsString.length() > 0) {
          try {
            publishDate = dateFormat.parse(dateAsString);
            if (publishDate.after(new Date())) {
              publishDate = new Date();
            }
          } catch (ParseException pe) {
            log.warn(pe);
          }
        }
      }

      // now save the published entry and remove the unpublished version
      try {
        log.info("Removing blog entry dated " + blogEntry.getDate());
        service.removeBlogEntry(blogEntry);

        blogEntry.setDate(publishDate);
        blogEntry.setPublished(true);
        log.info("Putting blog entry dated " + blogEntry.getDate());
        service.putBlogEntry(blogEntry);
      } catch (BlogException be) {
        log.error(be);
      }

    } else if (submit.equals("Unpublish")) {
      blogEntry.setPublished(false);
      try {
        service.putBlogEntry(blogEntry);
      } catch (BlogException be) {
        log.error(be);
      }
    }

    return new RedirectView(blogEntry.getLocalPermalink());
  }

  /**
   * Gets a list of all roles that are allowed to access this action.
   *
   * @return  an array of Strings representing role names
   * @param request
   */
  public String[] getRoles(HttpServletRequest request) {
    return new String[]{Constants.BLOG_OWNER_ROLE};
  }

}