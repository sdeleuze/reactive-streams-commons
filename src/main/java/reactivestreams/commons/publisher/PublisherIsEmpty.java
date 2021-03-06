package reactivestreams.commons.publisher;

import org.reactivestreams.*;

import reactivestreams.commons.subscriber.SubscriberDeferredScalar;
import reactivestreams.commons.support.SubscriptionHelper;

public final class PublisherIsEmpty<T> extends PublisherSource<T, Boolean> {

    public PublisherIsEmpty(Publisher<? extends T> source) {
        super(source);
    }

    @Override
    public void subscribe(Subscriber<? super Boolean> s) {
        source.subscribe(new PublisherIsEmptySubscriber<>(s));
    }

    static final class PublisherIsEmptySubscriber<T> extends SubscriberDeferredScalar<T, Boolean>
    implements Upstream {
        Subscription s;

        public PublisherIsEmptySubscriber(Subscriber<? super Boolean> actual) {
            super(actual);
        }

        @Override
        public void cancel() {
            super.cancel();
            s.cancel();
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.s, s)) {
                this.s = s;
                subscriber.onSubscribe(this);

                s.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T t) {
            s.cancel();

            complete(false);
        }

        @Override
        public void onComplete() {
            complete(true);
        }

        @Override
        public Object upstream() {
            return s;
        }
    }
}
