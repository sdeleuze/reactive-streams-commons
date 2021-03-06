package reactivestreams.commons.publisher;

import java.util.ArrayDeque;

import org.reactivestreams.*;

import reactivestreams.commons.support.SubscriptionHelper;

/**
 * Skips the last N elements from the source stream.
 *
 * @param <T> the value type
 */
public final class PublisherSkipLast<T> extends PublisherSource<T, T> {

    final int n;

    public PublisherSkipLast(Publisher<? extends T> source, int n) {
        super(source);
        if (n < 0) {
            throw new IllegalArgumentException("n >= 0 required but it was " + n);
        }
        this.n = n;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        if (n == 0) {
            source.subscribe(s);
        } else {
            source.subscribe(new PublisherSkipLastSubscriber<>(s, n));
        }
    }

    static final class PublisherSkipLastSubscriber<T> implements Subscriber<T>, Upstream, Downstream,
                                                                 Buffering {
        final Subscriber<? super T> actual;

        final int n;

        final ArrayDeque<T> buffer;

        Subscription s;

        public PublisherSkipLastSubscriber(Subscriber<? super T> actual, int n) {
            this.actual = actual;
            this.n = n;
            this.buffer = new ArrayDeque<>();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;

                actual.onSubscribe(s);

                s.request(n);
            }
        }

        @Override
        public void onNext(T t) {

            ArrayDeque<T> bs = buffer;

            if (bs.size() == n) {
                T v = bs.poll();

                actual.onNext(v);
            }
            bs.offer(t);

        }

        @Override
        public void onError(Throwable t) {
            actual.onError(t);
        }

        @Override
        public void onComplete() {
            actual.onComplete();
        }

        @Override
        public long pending() {
            return buffer.size();
        }

        @Override
        public long getCapacity() {
            return n;
        }

        @Override
        public Object downstream() {
            return actual;
        }

        @Override
        public Object upstream() {
            return s;
        }
    }
}
