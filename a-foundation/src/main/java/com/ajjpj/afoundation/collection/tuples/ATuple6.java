package com.ajjpj.afoundation.collection.tuples;


import java.io.Serializable;


/**
 * @author arno
 */
public class ATuple6<T1,T2,T3,T4,T5,T6> implements Serializable {
    public final T1 _1;
    public final T2 _2;
    public final T3 _3;
    public final T4 _4;
    public final T5 _5;
    public final T6 _6;

    public ATuple6 (T1 _1, T2 _2, T3 _3, T4 _4, T5 _5, T6 _6) {
        this._1 = _1;
        this._2 = _2;
        this._3 = _3;
        this._4 = _4;
        this._5 = _5;
        this._6 = _6;
    }

    @Override public String toString () {
        return "ATuple6{" +
                "_1=" + _1 +
                ", _2=" + _2 +
                ", _3=" + _3 +
                ", _4=" + _4 +
                ", _5=" + _5 +
                ", _6=" + _6 +
                '}';
    }

    @Override
    public boolean equals (Object o) {
        if (this == o) return true;
        if (o == null || getClass () != o.getClass ()) return false;

        ATuple6 aTuple6 = (ATuple6) o;

        if (_1 != null ? !_1.equals (aTuple6._1) : aTuple6._1 != null) return false;
        if (_2 != null ? !_2.equals (aTuple6._2) : aTuple6._2 != null) return false;
        if (_3 != null ? !_3.equals (aTuple6._3) : aTuple6._3 != null) return false;
        if (_4 != null ? !_4.equals (aTuple6._4) : aTuple6._4 != null) return false;
        if (_5 != null ? !_5.equals (aTuple6._5) : aTuple6._5 != null) return false;
        if (_6 != null ? !_6.equals (aTuple6._6) : aTuple6._6 != null) return false;

        return true;
    }
    @Override
    public int hashCode () {
        int result = _1 != null ? _1.hashCode () : 0;
        result = 31 * result + (_2 != null ? _2.hashCode () : 0);
        result = 31 * result + (_3 != null ? _3.hashCode () : 0);
        result = 31 * result + (_4 != null ? _4.hashCode () : 0);
        result = 31 * result + (_5 != null ? _5.hashCode () : 0);
        result = 31 * result + (_6 != null ? _6.hashCode () : 0);
        return result;
    }
}
