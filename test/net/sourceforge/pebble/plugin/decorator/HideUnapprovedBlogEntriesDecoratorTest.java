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
package net.sourceforge.pebble.plugin.decorator;

import net.sourceforge.pebble.util.SecurityUtils;
import net.sourceforge.pebble.domain.BlogEntry;
import net.sourceforge.pebble.domain.SingleBlogTestCase;
import net.sourceforge.pebble.domain.State;

/**
 * Tests for the HideUnapprovedBlogEntriesDecorator class.
 *
 * @author    Simon Brown
 */
public class HideUnapprovedBlogEntriesDecoratorTest extends SingleBlogTestCase {

  private BlogEntryDecorator decorator;
  private BlogEntry blogEntry;

  public void setUp() {
    super.setUp();

    decorator = new HideUnapprovedBlogEntriesDecorator();
    blogEntry = blog.getBlogForToday().createBlogEntry();
  }

  /**
   * Tests that unapproved blog entries are removed when not logged in.
   */
  public synchronized void testUnapprovedBlogEntriesRemovedWhenNotLoggedIn() throws Exception {
    blogEntry.setState(State.APPROVED);
    BlogEntryDecoratorChain chain = new BlogEntryDecoratorChain(null);
    BlogEntryDecoratorContext context = new BlogEntryDecoratorContext();
    context.setBlogEntry(blogEntry);

    decorator.decorate(chain, context);
    assertEquals(blogEntry, context.getBlogEntry());

    SecurityUtils.runAsAnonymous();
    blogEntry.setState(State.PENDING);
    decorator.decorate(chain, context);
    assertNull(context.getBlogEntry());
  }

  /**
   * Tests that unapproved comments and TrackBacks are not removed.
   */
  public synchronized void testUnapprovedBlogEntriesNotRemovedWhenLoggedIn() throws Exception {
    blogEntry.setState(State.PENDING);
    BlogEntryDecoratorChain chain = new BlogEntryDecoratorChain(null);
    BlogEntryDecoratorContext context = new BlogEntryDecoratorContext();
    context.setBlogEntry(blogEntry);

    SecurityUtils.runAsAnonymous();
    decorator.decorate(chain, context);
    assertNull(context.getBlogEntry());

    context.setBlogEntry(blogEntry);
    SecurityUtils.runAsBlogContributor();
    decorator.decorate(chain, context);
    assertEquals(blogEntry, context.getBlogEntry());

    context.setBlogEntry(blogEntry);
    SecurityUtils.runAsBlogOwner();
    decorator.decorate(chain, context);
    assertEquals(blogEntry, context.getBlogEntry());
  }

}