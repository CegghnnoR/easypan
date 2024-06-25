package com.easypan.aspect;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.exception.BusinessException;
import com.easypan.utils.StringTools;
import com.easypan.utils.VerifyUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Aspect
@Component("globalOperationAspect")
public class GlobalOperationAspect {
    private static final Logger logger = LoggerFactory.getLogger(GlobalOperationAspect.class);
    private static final String[] TYPE_BASE = {"java.lang.String", "java.lang.Integer", "java.lang.Long"};

    @Pointcut("@annotation(com.easypan.annotation.GlobalInterceptor)")
    private void requestInterceptor() {

    }

    @Before("requestInterceptor()")
    public void interceptorDo(JoinPoint point) throws BusinessException {
        try {
            // 获取目标对象（执行被拦截方法的对象）
            Object target = point.getTarget();
            // 获取方法参数
            Object[] args = point.getArgs();
            // 获取方法名
            String methodName = point.getSignature().getName();
            // 获取方法的参数类型
            Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
            // 通过反射获取方法对象
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            // 获取方法上的 @GlobalInterceptor 注解
            GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
            // 如果方法上没有 @GlobalInterceptor 注解，直接返回
            if (interceptor == null) {
                return;
            }
            // 校验登录
            if (interceptor.checkLogin() || interceptor.checkAdmin()) {
                checkLogin(interceptor.checkAdmin());
            }
            // 如果注解要求校验参数，则进行参数校验
            if (interceptor.checkParams()) {
                validateParams(method, args);
            }
        } catch (BusinessException e) {
            logger.error("全局拦截器异常", e);
            throw e;
        } catch (Throwable e) {
            logger.error("全局拦截器异常", e);
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    private void checkLogin(Boolean checkAdmin) {
        HttpServletRequest request = ((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
        HttpSession session = request.getSession();
        SessionWebUserDto userDto = (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
        if (userDto == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }
        if (checkAdmin && !userDto.getAdmin()) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }

    /**
     * 校验参数
     *
     * @param m 被拦截的方法
     * @param arguments 被拦截的方法的参数值数组
     */
    private void validateParams(Method m, Object[] arguments) {
        // 获取方法的所有参数
        Parameter[] parameters = m.getParameters();
        // 遍历每一个参数
        for (int i = 0; i < parameters.length; ++i) {
            Parameter parameter = parameters[i]; // 当前参数
            Object value = arguments[i]; // 当前参数的值
            // 获取参数上的 @VerifyParam 注解
            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);
            // 如果参数上没有 @VerifyParam 注解，则跳过该参数
            if (verifyParam == null) {
                continue;
            }
            // 如果参数是基本数据类型
            if (ArrayUtils.contains(TYPE_BASE, parameter.getParameterizedType().getTypeName())) {
                checkValue(value, verifyParam);
            } else {
                // 如果参数是对象类型
                checkObjValue(parameter, value);
            }
        }
    }

    /**
     * 通过反射机制获取对象的字段，并检查字段上的 @VerifyParam 注解，验证字段的值是否符合注解定义的条件。
     *
     * @param parameter Parameter 参数
     * @param value 参数的值
     */
    private void checkObjValue(Parameter parameter, Object value) {
        try {
            // 获取参数的类型名称
            String typeName = parameter.getParameterizedType().getTypeName();
            // 根据类型名称获取类对象
            Class<?> classz = Class.forName(typeName);
            // 获取类的所有字段
            Field[] fields = classz.getDeclaredFields();
            // 遍历每一个字段
            for (Field field : fields) {
                // 获取字段上的 @VerifyParam 注解
                VerifyParam fieldVerifyParam = field.getAnnotation(VerifyParam.class);
                // 如果字段上没有 @VerifyParam 注解，则跳过该字段
                if (fieldVerifyParam == null) {
                    continue;
                }
                // 设置字段可访问
                field.setAccessible(true);
                // 获取value对象中该字段的值
                Object resultValue = field.get(value);
                // 验证字段的值
                checkValue(resultValue, fieldVerifyParam);
            }
        } catch (BusinessException e) {
            logger.error("校验参数失败", e);
            throw e;
        } catch (Exception e) {
            logger.error("校验参数失败", e);
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    private void checkValue(Object value, VerifyParam verifyParam) {
        boolean isEmpty = value == null || StringTools.isEmpty(value.toString());
        int length = value == null ? 0 : value.toString().length();
        // 校验空
        if (isEmpty && verifyParam.required()) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        // 校验长度
        if (!isEmpty &&
            (verifyParam.max() != -1 && verifyParam.max() < length ||
            verifyParam.min() != -1 && verifyParam.min() > length)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        // 校验正则
        if (!isEmpty &&
            !StringTools.isEmpty(verifyParam.regex().getRegex()) &&
            !VerifyUtils.verify(verifyParam.regex(), String.valueOf(value))) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }
}
