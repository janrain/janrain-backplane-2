package com.janrain.servlet;

import com.janrain.backplane.server.InvalidRequestException;
import org.apache.log4j.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @author Johnny Bufu
 */
public class NYPostRefererFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("nypost filter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String referer = httpRequest.getHeader("referer");
        if (referer == null || ! referer.toLowerCase().contains("nypost.com")) {
            chain.doFilter(request,response);
        } else {
            throw new InvalidRequestException("Access restricted");
        }
    }

    @Override
    public void destroy() {
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(NYPostRefererFilter.class);

}
