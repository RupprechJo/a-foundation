package com.ajjpj.abase.function;


/**
 * @author arno
 */
public interface AFunction0NoThrow<R> extends AFunction0<R, RuntimeException> {
    @Override R apply();
}
