package com.alibaba.himarket.support.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** API 属性字段注解 用于描述 API 扩展属性的字段信息，用于前端动态生成表单 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface APIField {

    /** 字段显示名称 */
    String label();

    /** 字段描述 */
    String description() default "";

    /** 是否必填 */
    boolean required() default false;

    /** 默认值（字符串形式） */
    String defaultValue() default "";

    /** 字段类型（如果为空则根据 Java 类型自动推断） */
    String type() default "";

    /** 选项列表（用于 select 类型） */
    String[] options() default {};
}
