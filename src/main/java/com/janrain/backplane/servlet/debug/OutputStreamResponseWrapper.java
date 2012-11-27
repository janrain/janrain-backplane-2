/*
 * Copyright 2012 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.servlet.debug;

import com.janrain.commons.util.IOUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * For use by the {@link DebugResponseLoggingFilter}
 * See http://www.java-forums.org/java-servlet/20631-how-get-content-httpservletresponse.html
 */
public class OutputStreamResponseWrapper extends HttpServletResponseWrapper {

    // - PUBLIC

	public OutputStreamResponseWrapper(HttpServletResponse response,
			Class<? extends OutputStream> outputStreamClass) {
		super(response);
		origResponse = response;
		this.outputStreamClass = outputStreamClass;
	}

	public ServletOutputStream createOutputStream() throws IOException {
		try {
			Constructor<?> c = outputStreamClass
					.getConstructor();
			realOutputStream = (OutputStream) c.newInstance();
			return new ServletOutputStreamWrapper(realOutputStream);
		} catch (Exception ex) {
			throw new IOException("Unable to construct servlet output stream: "
					+ ex.getMessage(), ex);
		}
	}

	public void finishResponse() {
        IOUtils.closeSilently(writer);
        IOUtils.closeSilently(stream);
	}

	@Override
	public void flushBuffer() throws IOException {
		stream.flush();
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (writer != null) {
			throw new IllegalStateException(
					"getOutputStream() has already been called!");
		}

		if (stream == null) {
			stream = createOutputStream();
		}
		return stream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		if (writer != null) {
			return (writer);
		}

		if (stream != null) {
			throw new IllegalStateException(
					"getOutputStream() has already been called!");
		}

		stream = createOutputStream();
		writer = new PrintWriter(new OutputStreamWriter(stream, "UTF-8"));
		return (writer);
	}

	@Override
	public void setContentLength(int length) {
	}

    @Override
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
        super.addCookie(cookie);
    }

    @Override
    public void setHeader(String name, final String value) {
        headers.put(name, new ArrayList<String>() {{ add(value); }});
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        getHeaderValues(name).add(value);
        super.addHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
        super.setStatus(sc);    //To change body of overridden methods use File | Settings | File Templates.
    }

    /**
	 * Gets the underlying instance of the output stream.
	 *
	 * @return
	 */
	public OutputStream getRealOutputStream() {
		return realOutputStream;
	}

    public int getStatus() {
        return status;
    }

    public List<Cookie> getCookies() {
        return cookies;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    // - PROTECTED

    protected HttpServletResponse origResponse = null;
    protected OutputStream realOutputStream = null;
    protected ServletOutputStream stream = null;
    protected PrintWriter writer = null;

    // - PACKAGE

    Class<? extends OutputStream> outputStreamClass;

    // - PRIVATE

    private int status;
    private List<Cookie> cookies = new ArrayList<Cookie>();
    private Map<String,List<String>> headers = new LinkedHashMap<String, List<String>>();

    private List<String> getHeaderValues(String headerName) {
        List<String> values = headers.get(headerName);
        if (values == null) {
            values = new ArrayList<String>();
            headers.put(headerName, values);
        }
        return values;
    }
}
