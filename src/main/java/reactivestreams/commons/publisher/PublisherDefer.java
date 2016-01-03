package reactivestreams.commons.publisher;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactivestreams.commons.subscription.EmptySubscription;
import reactivestreams.commons.support.ReactiveState;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Defers the creation of the actual Publisher the Subscriber will be subscribed to.
 *
 * @param <T> the value type
 */
public final class PublisherDefer<T> implements Publisher<T>,
                                                ReactiveState.Factory,
                                                ReactiveState.Upstream {

    final Supplier<? extends Publisher<? extends T>> supplier;

    public PublisherDefer(Supplier<? extends Publisher<? extends T>> supplier) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    @Override
    public Object upstream() {
        return supplier;
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        Publisher<? extends T> p;

        try {
            p = supplier.get();
        } catch (Throwable e) {
            EmptySubscription.error(s, e);
            return;
        }

        if (p == null) {
            EmptySubscription.error(s, new NullPointerException("The Producer returned by the supplier is null"));
            return;
        }

        p.subscribe(s);
    }
}
