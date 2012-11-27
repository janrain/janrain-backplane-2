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

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * For use by the {@link DebugResponseLoggingFilter}
 * See http://www.java-forums.org/java-servlet/20631-how-get-content-httpservletresponse.html
 */
public class ServletOutputStreamWrapper extends ServletOutputStream {
	OutputStream _out;
	boolean closed = false;

	public ServletOutputStreamWrapper(OutputStream realStream) {
		this._out = realStream;
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			throw new IOException("This output stream has already been closed");
		}
		_out.flush();
		_out.close();

		closed = true;
	}

	@Override
	public void flush() throws IOException {
		if (closed) {
			throw new IOException("Cannot flush a closed output stream");
		}
		_out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		if (closed) {
			throw new IOException("Cannot write to a closed output stream");
		}
		_out.write((byte) b);
	}

	@Override
	public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte b[], int off, int len) throws IOException {
		// System.out.println("writing...");
		if (closed) {
			throw new IOException("Cannot write to a closed output stream");
		}
		_out.write(b, off, len);
	}

}
