package com.netflix.conductor.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstraintParamUtil {

    public static String[] extractParamPathComponents(String inputParam){
        String[] paramPathComponents = null;
        Pattern pattern = Pattern.compile("^\\$\\{(.+)}$");
        Matcher matcher = pattern.matcher(inputParam);

        if (matcher.find()) {
            String inputVariable = matcher.group(1);
            paramPathComponents = inputVariable.split("\\.");
        }

        return paramPathComponents;
    }
}
