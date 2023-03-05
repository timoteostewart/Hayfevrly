package Hayfevrly.Model;

import java.util.Objects;

public class Utils {

    public static class Pair<F, S> {

        // code from Android Open Source Project, Apache 2.0 license

        public final F first;
        public final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public static <A, B> Pair<A, B> create(A a, B b) {
            return new Pair<A, B>(a, b);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pair)) {
                return false;
            }

            Pair<?, ?> p = (Pair<?, ?>) o;
            return Objects.equals(p.first, first) && Objects.equals(p.second, second);
        }

        @Override
        public int hashCode() {
            return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
        }
    }


}
