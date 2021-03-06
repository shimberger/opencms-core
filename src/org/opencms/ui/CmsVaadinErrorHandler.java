/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ui;

import org.opencms.main.CmsLog;

import java.net.SocketException;

import org.apache.commons.logging.Log;

import com.vaadin.server.DefaultErrorHandler;
import com.vaadin.server.ErrorEvent;

/**
 * Error handler for uncaught Vaadin exceptions.<p>
 */
public class CmsVaadinErrorHandler extends DefaultErrorHandler {

    /** Serial version id. */
    private static final long serialVersionUID = 1L;

    /** The logger instance for this class. */
    private static final Log LOG = CmsLog.getLog(CmsVaadinErrorHandler.class);

    /**
     * @see com.vaadin.server.DefaultErrorHandler#error(com.vaadin.server.ErrorEvent)
     */
    @Override
    public void error(ErrorEvent event) {

        super.error(event);
        Throwable throwable = event.getThrowable();
        if (!(throwable instanceof SocketException)) {
            LOG.error(throwable.getLocalizedMessage(), throwable);
        }
    }

}
