package br.com.oficina.billing.infrastructure.transaction;

import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Coloca cada chamada de um caso de uso dentro de uma transação, sem que a camada de aplicação
 * (Java puro) precise conhecer o Spring: se o método termina normalmente a transação confirma;
 * se lança uma exceção não verificada, desfaz tudo e a exceção original sobe para quem chamou.
 */
public final class TransacaoProxy {

    private TransacaoProxy() {
    }

    public static <T> T envolver(Class<T> porta, T alvo, TransactionTemplate transacao) {
        Object proxy = Proxy.newProxyInstance(porta.getClassLoader(), new Class<?>[]{porta},
                (p, metodo, args) -> {
                    if (metodo.getDeclaringClass() == Object.class) {
                        return invocar(metodo, alvo, args);
                    }
                    return transacao.execute(status -> invocar(metodo, alvo, args));
                });
        return porta.cast(proxy);
    }

    private static Object invocar(java.lang.reflect.Method metodo, Object alvo, Object[] args) {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException e) {
            Throwable causa = e.getTargetException();
            if (causa instanceof RuntimeException re) {
                throw re;
            }
            if (causa instanceof Error erro) {
                throw erro;
            }
            throw new UndeclaredThrowableException(causa);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
