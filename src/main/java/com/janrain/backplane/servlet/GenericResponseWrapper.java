package com.janrain.backplane.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;

public class GenericResponseWrapper extends HttpServletResponseWrapper {
    private ByteArrayOutputStream output;
    private int contentLength;
    private String contentType;

    public GenericResponseWrapper(HttpServletResponse response) {
        super(response);
        output=new ByteArrayOutputStream();
    }

    public byte[] getData() {
        try {
            output.flush();
            output.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return output.toByteArray();
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new FilterServletOutputStream(output);
    }

    @Override
    public PrintWriter getWriter() {
        return new PrintWriter(getOutputStream(),true);
    }

    @Override
    public void setContentLength(int length) {
        this.contentLength = length;
        super.setContentLength(length);
    }

    public int getContentLength() {
        return contentLength;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
        super.setContentType(type);
    }

    @Override
    public String getContentType() {
        return contentType;
    }
}
