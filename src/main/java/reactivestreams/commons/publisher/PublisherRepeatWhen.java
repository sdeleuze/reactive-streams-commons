package reactivestreams.commons.publisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;

import org.reactivestreams.*;

import reactivestreams.commons.processor.SimpleProcessor;
import reactivestreams.commons.subscriber.*;
import reactivestreams.commons.subscription.DeferredSubscription;
import reactivestreams.commons.subscription.EmptySubscription;

/**
 * Repeats a source when a companion sequence
 * signals an item in response to the main's completion signal
 * <p>
 * <p>If the companion sequence signals when the main source is active, the repeat
 * attempt is suppressed and any terminal signal will terminate the main source with the same signal immediately.
 *
 * @param <T> the source value type
 */
public final class PublisherRepeatWhen<T> extends PublisherSource<T, T> {

    final Function<? super PublisherBase<Object>, ? extends Publisher<? extends Object>> whenSourceFactory;

    public PublisherRepeatWhen(Publisher<? extends T> source,
                               Function<? super PublisherBase<Object>, ? extends Publisher<? extends Object>> whenSourceFactory) {
        super(source);
        this.whenSourceFactory = Objects.requireNonNull(whenSourceFactory, "whenSourceFactory");
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {

        PublisherRepeatWhenOtherSubscriber other = new PublisherRepeatWhenOtherSubscriber();

        SerializedSubscriber<T> serial = new SerializedSubscriber<>(s);

        PublisherRepeatWhenMainSubscriber<T> main = new PublisherRepeatWhenMainSubscriber<>(serial, other
          .completionSignal, source);
        other.main = main;

        serial.onSubscribe(main);

        Publisher<? extends Object> p;

        try {
            p = whenSourceFactory.apply(other);
        } catch (Throwable e) {
            s.onError(e);
            return;
        }

        if (p == null) {
            s.onError(new NullPointerException("The whenSourceFactory returned a null Publisher"));
            return;
        }

        p.subscribe(other);

        if (!main.cancelled) {
            source.subscribe(main);
        }
    }

    static final class PublisherRepeatWhenMainSubscriber<T> extends SubscriberMultiSubscription<T, T> {

        final DeferredSubscription otherArbiter;

        final Subscriber<Object> signaller;

        final Publisher<? extends T> source;

        volatile int wip;
        @SuppressWarnings("rawtypes")
        static final AtomicIntegerFieldUpdater<PublisherRepeatWhenMainSubscriber> WIP =
          AtomicIntegerFieldUpdater.newUpdater(PublisherRepeatWhenMainSubscriber.class, "wip");

        volatile boolean cancelled;

        static final Object NEXT = new Object();

        public PublisherRepeatWhenMainSubscriber(Subscriber<? super T> actual, Subscriber<Object> signaller,
                                                 Publisher<? extends T> source) {
            super(actual);
            this.signaller = signaller;
            this.source = source;
            this.otherArbiter = new DeferredSubscription();
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;

            cancelWhen();

            super.cancel();
        }

        void cancelWhen() {
            otherArbiter.cancel();
        }

        public void setWhen(Subscription w) {
            otherArbiter.set(w);
        }

        @Override
        public void onSubscribe(Subscription s) {
            set(s);
        }

        @Override
        public void onNext(T t) {
            subscriber.onNext(t);

            producedOne();
        }

        @Override
        public void onError(Throwable t) {
            otherArbiter.cancel();

            subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            otherArbiter.request(1);

            signaller.onNext(NEXT);
        }

        void resubscribe() {
            if (WIP.getAndIncrement(this) == 0) {
                do {
                    if (cancelled) {
                        return;
                    }

                    source.subscribe(this);

                } while (WIP.decrementAndGet(this) != 0);
            }
        }

        void whenError(Throwable e) {
            cancelled = true;
            super.cancel();

            subscriber.onError(e);
        }

        void whenComplete() {
            cancelled = true;
            super.cancel();

            subscriber.onComplete();
        }
    }

    static final class PublisherRepeatWhenOtherSubscriber 
    extends PublisherBase<Object>
    implements Subscriber<Object>, FeedbackLoop, Trace, Inner {
        PublisherRepeatWhenMainSubscriber<?> main;

        final SimpleProcessor<Object> completionSignal = new SimpleProcessor<>();

        @Override
        public void onSubscribe(Subscription s) {
            main.setWhen(s);
            completionSignal.onSubscribe(EmptySubscription.INSTANCE);
        }

        @Override
        public void onNext(Object t) {
            main.resubscribe();
        }

        @Override
        public void onError(Throwable t) {
            main.whenError(t);
        }

        @Override
        public void onComplete() {
            main.whenComplete();
        }

        @Override
        public void subscribe(Subscriber<? super Object> s) {
            completionSignal.subscribe(s);
        }

        @Override
        public Object delegateInput() {
            return main;
        }

        @Override
        public Object delegateOutput() {
            return completionSignal;
        }
    }
}
