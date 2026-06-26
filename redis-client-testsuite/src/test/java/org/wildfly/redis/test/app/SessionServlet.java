/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.redis.test.app;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/session")
public class SessionServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain");
        String action = req.getParameter("action");

        if ("set".equals(action)) {
            HttpSession session = req.getSession(true);
            String key = req.getParameter("key");
            String value = req.getParameter("value");
            session.setAttribute(key, value);
            resp.getWriter().println("sessionId=" + session.getId());
            resp.getWriter().println("key=" + key);
            resp.getWriter().println("value=" + value);
        } else if ("get".equals(action)) {
            HttpSession session = req.getSession(false);
            if (session == null) {
                resp.setStatus(404);
                resp.getWriter().println("no-session");
                return;
            }
            String key = req.getParameter("key");
            Object value = session.getAttribute(key);
            resp.getWriter().println("sessionId=" + session.getId());
            resp.getWriter().println("key=" + key);
            resp.getWriter().println("value=" + (value != null ? value : "null"));
        } else if ("invalidate".equals(action)) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                String id = session.getId();
                session.invalidate();
                resp.getWriter().println("invalidated=" + id);
            } else {
                resp.getWriter().println("no-session");
            }
        } else {
            HttpSession session = req.getSession(false);
            if (session != null) {
                resp.getWriter().println("sessionId=" + session.getId());
                resp.getWriter().println("isNew=" + session.isNew());
            } else {
                resp.getWriter().println("no-session");
            }
        }
    }
}
