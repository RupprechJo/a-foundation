package com.ajjpj.afoundation.collection.tuples;


import java.io.Serializable;


/**
 * @author arno
 */
public class ATuple3<T1,T2,T3> implements Serializable {
    public final T1 _1;
    public final T2 _2;
    public final T3 _3;

    public ATuple3 (T1 _1, T2 _2, T3 _3) {
        this._1 = _1;
        this._2 = _2;
        this._3 = _3;
    }

    @Override public String toString () {
        return "ATuple3{" +
                "_1=" + _1 +
                ", _2=" + _2 +
                ", _3=" + _3 +
                '}';
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass () != o.getClass ()) return false;

        ATuple3 aTuple3 = (ATuple3) o;

        if (_1 != null ? !_1.equals (aTuple3._1) : aTuple3._1 != null) return false;
        if (_2 != null ? !_2.equals (aTuple3._2) : aTuple3._2 != null) return false;
        if (_3 != null ? !_3.equals (aTuple3._3) : aTuple3._3 != null) return false;

        return true;
    }
    @Override
    public int hashCode () {
        int result = _1 != null ? _1.hashCode () : 0;
        result = 31 * result + (_2 != null ? _2.hashCode () : 0);
        result = 31 * result + (_3 != null ? _3.hashCode () : 0);
        return result;
    }
}
