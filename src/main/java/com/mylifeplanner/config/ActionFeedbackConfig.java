package com.mylifeplanner.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.RequestContextUtils;

@Configuration
public class ActionFeedbackConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response,
                                   Object handler, ModelAndView view) {
                if (view == null || view.getViewName() == null || !view.getViewName().startsWith("redirect:")
                        || response.getStatus() >= 400) return;
                var flash = RequestContextUtils.getOutputFlashMap(request);
                if (flash.containsKey("categoryError") || flash.containsKey("cycleError")
                        || flash.containsKey("actionError") || flash.containsKey("actionSuccess")) return;
                String path = request.getRequestURI();
                boolean delete = path.endsWith("/delete");
                boolean update = path.endsWith("/edit") || path.contains("/status/") || path.endsWith("/complete")
                        || (request.getParameter("id") != null && !request.getParameter("id").isBlank());
                if (!"POST".equals(request.getMethod()) && !delete && !update) return;
                flash.put("actionSuccess", delete ? "El registro se elimin\u00f3 correctamente."
                        : update ? "Los cambios se guardaron correctamente."
                        : "El registro se guard\u00f3 correctamente.");
            }
        }).addPathPatterns("/finances/**", "/tasks/**", "/activities/**", "/goals/**");
    }
}
