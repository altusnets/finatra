.. _basics:

Dependency Injection
--------------------

The Finatra framework uses `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_ (DI) and it is important to understand the concept -- specifically how it relates to effective testing which helps to explain the motivations of the framework.

`Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_ is one way to implement the `Inversion of Control (IoC) <https://en.wikipedia.org/wiki/Inversion_of_control>`_ programming principle. More importantly `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_ is a **design pattern** and does not refer to any specific implementation of a library.

Finatra does use the Google `Guice <https://github.com/google/guice>`__ `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_ library which is also available for service writers if they choose to use `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_. However, the framework was designed around the principle of `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_, not the `Guice <https://github.com/google/guice>`_ library implementation with a primary goal to build testability of code into the framework.

With that, a great place to start on understanding the reasoning behind `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_  is the `Motivation <https://github.com/google/guice/wiki/Motivation>`__ section of the Google `Guice <https://github.com/google/guice>`__ framework.

.. attention::
  You are **not required** to use Google `Guice <https://github.com/google/guice>`__ `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_ when using Finatra. Creating servers, wiring in controllers and applying filters can all be done without using any dependency injection. However, you will not be able to take full-advantage of Finatra's `testing <../testing/index.html>`__ features.

A simple example of Finatra's `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_  integration is adding controllers to Finatra's `HttpRouter <https://github.com/twitter/finatra/blob/develop/http/src/main/scala/com/twitter/finatra/http/routing/HttpRouter.scala>`__ *by type*:

.. code:: scala

    class Server extends HttpServer {
      override def configureHttp(router: HttpRouter) {
        router.add[MyController]
      }
    }

As mentioned, it is also possible to do this without using `Guice <https://github.com/google/guice>`__: simply instantiate your controller and add the instance to the router:

.. code:: scala

    class NonDIServer extends HttpServer {
      val myController = new MyController(...)

      override def configureHttp(router: HttpRouter) {
        router.add(myController)
      }
    }

Dependency Injection and Testing
--------------------------------

There are many resources around this topic but we recommend taking a look at `The Tao of Testing: Chapter 3 - Dependency Injection <https://jasonpolites.github.io/tao-of-testing/ch3-1.1.html>`__ as a primer for how `Dependency Injection <https://en.wikipedia.org/wiki/Dependency_injection>`_ can help to write more testable code.

Dependency Injection Best Practices
-----------------------------------

To aid in making code more testable here are a few best practices to consider when working with Dependency Injection. These are generally good to follow regardless of the dependency injection framework being used and some are described in detail in the Google `Guice documentation <https://github.com/google/guice/wiki>`__.

Use Constructor Injection
=========================

Finatra highly recommends using constructor injection to create immutable objects. Immutability allows objects to be simple, shareable and composable. For example:

.. code:: scala

    class RealPaymentService @Inject()(
      paymentQueue: PaymentQueue, 
      notifier: Notifier 
    )

or in Java

.. code:: java

    public class RealPaymentService {
      private final PaymentQueue paymentQueue;
      private final Notifier notifier;

      @Inject
      public RealPaymentService(
        PaymentQueue paymentQueue, 
        Notifier notifier
      ) { 
        this.paymentQueue = paymentQueue; 
        this.notifier = notifier; 
      }
    }

This clearly expresses that the `RealPaymentService` class requires a `PaymentQueue` and a `Notifier` for instantiation and allows for instantiation via injector *or* manual instantiation of the class. This way of defining injectable members for a class is preferable to `Field Injection <https://github.com/google/guice/wiki/Injections#field-injection>`__ or `Method Injection <https://github.com/google/guice/wiki/Injections#method-injection>`__. 

If you want to be able to inject a type that is not a class you own, then prefer defining a `Module <./modules.html>`__ which can provide an (ideally immutable) instance to the object graph.

.. note::

  The Guice documentation recommends `keeping constructors hidden <https://github.com/google/guice/wiki/KeepConstructorsHidden>`__ for classes which are meant to be instantiated by the injector. If you prefer constructor injection to create immutable objects, the difference between manual instantiation and injector instantiation should only come down to scoping of the created instance, i.e., is it a Singleton (the same instance every time from the injector). Finatra takes the approach of the `@Inject` annotation being metadata in that it signals that the class *can* be instantiated by the injector but it is not a requirement.

  We leave reducing the visibility of constructors to your discretion as a matter of what makes sense for your project or team.

Inject direct dependencies
==========================

Avoid injecting an object simply as a way to get another object. For instance do not inject a `Customer` simply to obtain an `Account`:

.. code:: scala

    class Budget @Inject()(
      customer: Customer
    ) {
      val account: Account = customer.purchasingAccount
    }

Instead, you should prefer to inject this dependency directly since this can make testing easier because your tests do not need to know anything about the `Customer` object. This is where defining a `Module <./modules.html>`__ is useful. Using an `@Provides`-annotated method you can create a binding for `Account` which comes from a `Customer` binding:

.. code:: scala

    class CustomersModule extends TwitterModule {

      @Provides
      def providePurchasingAccount(
        customer: Customer
      ): Account = {
        customer.purchasingAccount
      }
    }

Injecting this binding makes the code simpler:

.. code:: scala

    class Budget @Inject()(account: Account)


Avoid cyclic dependencies
=========================

This is good practice in general and cycles often reflect insufficiently granular decomposition. For instance, assume you have a `Store`, a `Boss`, and a `Clerk`.

.. code:: scala

    class Store @Inject()(boss: Boss) {
      def incomingCustomer(customer: Customer): Unit = ???
      def nextCustomer: Customer = ???
    }

    class Boss @Inject()(clerk: Clerk)

    class Clerk()

So far, so good. Constructing a `Store` constructs a new `Boss` which in turn constructs a new `Clerk`. However, to give the `Clerk` a `Customer` in order to make a sale, the `Clerk` needs a reference to the `Store` to get those customers:

.. code:: scala

    class Store @Inject()(boss: Boss) {
      def incomingCustomer(customer: Customer): Unit = ???
      def nextCustomer: Customer = ???
    }

    class Boss @Inject()(clerk: Clerk)

    class Clerk @Inject()(store: Store) {
      def doSale(): Unit = {
        val customer = store.nextCustomer
        ...
      }
    }

which now leads to a cycle, `Clerk` -> `Store` -> `Boss` -> `Clerk`.

Eliminate the cycle (preferred)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

One way to eliminate such cycles is to extract the dependency case into a separate class. In the contrived example, we could introduce a way of representing a line of eager customers as a `CustomerLine` which can be injected into a `Clerk` or a `Store`.

.. code:: scala

    class Store @Inject()(boss: Boss, customers: CustomerLine) {
      def incomingCustomer(customer: Customer): Unit = ???
      def nextCustomer: Customer = ???
    }

    class Clerk @Inject()(customers: CustomerLine) {
      def doSale(): Unit = {
        val customer = customers.nextCustomer
        ...
      }
    }

`Store` and `Clerk` now both depend on a `CustomerLine` (you should ensure that they use the same instance) and thus no longer a cycle in the graph.

Use a Provider
~~~~~~~~~~~~~~

`Injecting a Provider <https://github.com/google/guice/wiki/InjectingProviders>`__ allows you to inject a seam into the dependency graph. In this case, the `Clerk` still depends on a `Store` but the `Clerk` does not dereference the `Store` until needed by asking for it from the `Provider[Store]`:

.. code:: scala

    class Clerk @Inject()(
      Provider[Store] storeProvider
    ) {
      def doSale(): Unit = {
        val customer = storeProvider.get.nextCustomer
      }
    }

Note: you should ensure in this case that the `Store` is bound as a `Singleton` (otherwise the `Provider.get` will instantiate a new `Store` which ends up in the cycle).

Avoid I/O with Providers
========================

As we saw in the above example, Providers can be a useful API but it lacks some semantics that you should be aware of:

* If you need to recover from specific failures the Provider API only returns a generic `ProvisionException`. You can `iterate through the causes <https://google.github.io/guice/api-docs/latest/javadoc/com/google/inject/ProvisionException.html#getErrorMessages()>`__ but you will not be able to catch the specific type. See `ThrowingProviders <https://github.com/google/guice/wiki/ThrowingProviders>`__ for a way to declare thrown exceptions from a `Provider`.
* No support for timeout. Thus you can deadlock waiting on the `Provider` to be available with a call to `Provider.get`.
* There is no retry for obtaining the instance from a Provider. If `Provider.get` is unavailable, multiple calls to `get` may simply throw multiple exceptions.

Avoid conditional logic in modules
==================================

It can be tempting to create `Modules <./modules.html>`__ which have conditional logic and can be configured to operate differently for different environments. We strongly recommend avoiding this pattern and the framework provides utilities (including `Flags <./flags.html>`__) to help in this regard. 

Please avoid doing this:

.. code:: scala

    // DO NOT DO THIS
    class FooModule(fooServer: String) extends TwitterModule {
      override protected def configure(): Unit = {
        if (fooServer != null) {
          bind[String](Names.named("fooServer")).toInstance(fooServer)
          bind[FooService].to[RemoteService]
        } else {
          bind[FooService].to[InMemoryFooService]
        }
      }
    }

    // NOR THIS
    class FooModule extends TwitterModule {
      val env = flag("env", "remote", "the environment")

      override protected def configure(): Unit = {
        if (env() == "remote") {
          bind[String](Names.named("fooServer")).toInstance(fooServer)
          bind[FooService].to[RemoteService]
        } else {
          bind[FooService].to[InMemoryFooService]
        }
      }
    }

We **strongly** recommend that *only production code ever be deployed to production* and thus configuration which should change per environment be externalized via `Flags <./flags.html>`__ and logic that should change per environment be encapsulated within `override modules <../testing/override_modules.html>`__ (that are not located with the production code -- e.g., production code in `src/main/scala` and test code in `src/test/scala`).

For more information see the sections on `Flags <./flags.html>`__, `Modules <./modules.html>`__, and `Override Modules <../testing/override_modules.html>`__.

Make use of the `TwitterModule` lifecycle
=========================================

Finatra adds a lifecycle to Modules which is directly tied to the `server (or application) lifecycle <./lifecycle.html>`__ in which the module is used. This allows users to overcome some of the limitations of a standard `AbstractModule`.

The `c.t.inject.TwitterModuleLifecycle <https://github.com/twitter/finatra/blob/f275df9ea4e00ea3690e63e72a5445bb2c98cea7/inject/inject-core/src/main/scala/com/twitter/inject/TwitterModuleLifecycle.scala#L9>`__ defines several phases which allow users to setup and teardown or close `Singleton` scoped resources. Thus, you are able to bind closable resources with a defined way to release them.

.. important:: 

    Please note that the lifecycle is for **Singleton**-scoped resources and users should still avoid binding unscoped resources without ways to shutdown or close them.

See: Guice's documentation on `Modules should be fast and side-effect free <https://github.com/google/guice/wiki/ModulesShouldBeFastAndSideEffectFree>`__ and `Avoid Injecting Closable Resources <https://github.com/google/guice/wiki/Avoid-Injecting-Closable-Resources>`__.

For more information on Finatra Modules see the documentation `here <./modules.html>`__.
