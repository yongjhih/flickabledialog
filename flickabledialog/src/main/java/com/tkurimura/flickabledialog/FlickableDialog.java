package com.tkurimura.flickabledialog;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.functions.Predicate;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;

public class FlickableDialog extends DialogFragment {

  protected static final String LAYOUT_RESOURCE_KEY = "layout_resource_bundle_key";
  protected static final String ROTATE_ANIMATION_KEY = "rotate_animation_key";
  protected static final String DISMISS_THRESHOLD_KEY = "layout_resource_bundle_key";
  protected static final String BACKGROUND_COLOR_RESOURCE_KEY = "color_resource_bundle_key";
  private boolean touchedTopArea;

  private float DISMISS_THRESHOLD = 700f;
  private float ROTATE_ANIMATION_EXPONENT = 30f;
  private CompositeDisposable compositeSubscription = new CompositeDisposable();
  private int previousX;
  private int previousY;
  private Integer defaultLeft;
  private Integer defaultTop;
  private boolean cancelAndDismissTaken = true;
  private boolean cancelable = false;

  public static FlickableDialog newInstance(@LayoutRes int layoutResources) {

    Bundle bundle = new Bundle();
    bundle.putInt(LAYOUT_RESOURCE_KEY, layoutResources);

    FlickableDialog flickableDialog = new FlickableDialog();
    flickableDialog.setArguments(bundle);

    return flickableDialog;
  }

  public static FlickableDialog newInstance(@LayoutRes int layoutResources,
      float animationThreshold, float rotateAnimationAmount, @ColorRes int backgroundColor) {

    Bundle bundle = new Bundle();
    bundle.putInt(LAYOUT_RESOURCE_KEY, layoutResources);

    if (animationThreshold != 0) {

      bundle.putFloat(DISMISS_THRESHOLD_KEY, animationThreshold);
    }
    if (rotateAnimationAmount != 0) {

      bundle.putFloat(ROTATE_ANIMATION_KEY, rotateAnimationAmount);
    }

    if (backgroundColor != 0) {

      bundle.putInt(BACKGROUND_COLOR_RESOURCE_KEY, backgroundColor);
    }

    FlickableDialog flickableDialog = new FlickableDialog();

    flickableDialog.setArguments(bundle);

    return flickableDialog;
  }

  /**
   * callback flicking amount from original position to dismiss threshold.
   * This method is aimed to be overridden
   *
   * @param verticalPercentage vertical flicking amount(-100 : top, 0 : origin. 100 : right)
   * @param horizontalPercentage horizontal flicking amount(-100 : left, 0 : origin. 100 : right)
   * @version 0.9.0
   */
  public void onFlicking(float verticalPercentage, float horizontalPercentage) {}

  /**
   * callback when dialog comes back to default position
   * This method is aimed to be overridden
   *
   * @version 0.9.0
   */
  public void onOriginBack() {}

  @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
    super.onCreateDialog(savedInstanceState);

    Bundle bundle = getArguments();

    @LayoutRes final int layoutResource = bundle.getInt(LAYOUT_RESOURCE_KEY);

    DISMISS_THRESHOLD = bundle.getFloat(DISMISS_THRESHOLD_KEY, DISMISS_THRESHOLD);
    ROTATE_ANIMATION_EXPONENT = bundle.getFloat(DISMISS_THRESHOLD_KEY, ROTATE_ANIMATION_EXPONENT);
    int backgroundColorResource = bundle.getInt(BACKGROUND_COLOR_RESOURCE_KEY, 0);

    final FrameLayout frameLayout = new FrameLayout(getContext());

    if (backgroundColorResource != 0) {
      frameLayout.setBackgroundColor(ContextCompat.getColor(getContext(), backgroundColorResource));
    } else {
      frameLayout.setBackgroundColor(Color.argb(100, 0, 0, 0));
    }

    compositeSubscription.add(Observable.create(new ObservableOnSubscribe<View>() {
      @Override public void subscribe(final ObservableEmitter<View> subscriber) {
        frameLayout.setOnClickListener(new View.OnClickListener() {
          @Override public void onClick(View v) {
            subscriber.onNext(v);
          }
        });
      }
    }).filter(new Predicate<View>() {
      @Override public boolean test(View view) {
        return cancelAndDismissTaken;
      }
    }).map(new Function<View, ObjectAnimator>() {
      @Override public ObjectAnimator apply(View view) {
        ObjectAnimator alphaAnimation = ObjectAnimator.ofFloat(frameLayout, "alpha", 1f, 0f);
        alphaAnimation.setDuration(300);

        return alphaAnimation;
      }
    }).flatMap(new Function<ObjectAnimator, Observable<?>>() {
      @Override public Observable<?> apply(ObjectAnimator objectAnimator) {

        objectAnimator.start();

        return Observable.just(1)
            .delay(300, TimeUnit.MILLISECONDS)
            .observeOn(mainThread());
      }
    }).doOnNext(new Consumer<Object>() {
      @Override public void accept(Object o) {

        if(onFlickableDialogCanceled != null){
          onFlickableDialogCanceled.onFlickableDialogCanceled();
        }
      }
    }).subscribe(new Consumer<Object>() {
      @Override public void accept(Object o) {
        dismiss();
      }
    }));

    compositeSubscription.add(Observable.create(new ObservableOnSubscribe<Pair<View, MotionEvent>>() {
        @Override public void subscribe(final ObservableEmitter<Pair<View, MotionEvent>> subscriber) {
            // create touch event observable

            final ViewGroup dialogView = (ViewGroup) LayoutInflater.from(getActivity())
                .inflate(layoutResource, frameLayout, true);

            dialogView.getChildAt(0).setOnTouchListener(new View.OnTouchListener() {
              @Override public boolean onTouch(View v, MotionEvent event) {

                subscriber.onNext(Pair.create(v, event));
                return true;
              }
            });
          }
        }).doOnNext(new Consumer<Pair<View, MotionEvent>>() {
          @Override public void accept(Pair<View, MotionEvent> viewMotionEventPair) {
            // memorize default content position as member variables
            if (defaultLeft == null || defaultTop == null) {
              // the first initial position
              defaultLeft = viewMotionEventPair.first.getLeft();
              defaultTop = viewMotionEventPair.first.getTop();
            }
          }
        }).doOnNext(new Consumer<Pair<View, MotionEvent>>() {

          @Override public void accept(Pair<View, MotionEvent> viewMotionEventPair) {
            // memorize touched down position as member variables
            final View rootView = viewMotionEventPair.first;
            final MotionEvent event = viewMotionEventPair.second;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {

              final float height = rootView.getHeight();

              final float initY = rootView.getY();

              final float eventRawY = event.getRawY();

              final float verticalCenter = initY + height / 2;

              touchedTopArea = eventRawY < verticalCenter;
            }
          }
        }).flatMap(new Function<Pair<View, MotionEvent>, Observable<Pair<View, MotionEvent>>>() {
          // move view with finger and rotate view as touched down position
          @Override public Observable<Pair<View, MotionEvent>> apply(
              final Pair<View, MotionEvent> viewMotionEventPair) {

            return Observable.zip(Observable.just(viewMotionEventPair)
                .map(new Function<Pair<View, MotionEvent>, Float>() {
                  @Override public Float apply(Pair<View, MotionEvent> viewMotionEventPair) {

                    return (float) (viewMotionEventPair.first.getLeft() - defaultLeft);
                  }
                }), Observable.just(viewMotionEventPair)
                .map(new Function<Pair<View, MotionEvent>, Pair<Integer, Integer>>() {
                  @Override
                  public Pair<Integer, Integer> apply(Pair<View, MotionEvent> viewMotionEventPair) {

                    int currentX = (int) viewMotionEventPair.second.getRawX();
                    int currentY = (int) viewMotionEventPair.second.getRawY();

                    final int left = viewMotionEventPair.first.getLeft() + (currentX - previousX);
                    final int top = viewMotionEventPair.first.getTop() + (currentY - previousY);

                    return Pair.create(left, top);
                  }
                }), new BiFunction<Float, Pair<Integer,Integer>, Pair<View,MotionEvent>>() {
              @Override public Pair<View, MotionEvent> apply(Float verticalGap,
                  Pair<Integer, Integer> leftTopPair) {
                if (viewMotionEventPair.second.getAction() == MotionEvent.ACTION_MOVE) {
                  // rotation
                  if (touchedTopArea) {
                    viewMotionEventPair.first.setRotation(verticalGap / -ROTATE_ANIMATION_EXPONENT);
                  } else {
                    viewMotionEventPair.first.setRotation(verticalGap / ROTATE_ANIMATION_EXPONENT);
                  }

                  // position
                  View rootView = viewMotionEventPair.first;
                  rootView.layout(leftTopPair.first, leftTopPair.second,
                      leftTopPair.first + rootView.getWidth(),
                      leftTopPair.second + rootView.getHeight());
                }
                return viewMotionEventPair;
              }
            });
          }
        }).doOnNext(new Consumer<Pair<View, MotionEvent>>() {
          @Override public void accept(Pair<View, MotionEvent> pair) {
            // memorize previous position as member variables

            final MotionEvent event = pair.second;

            previousX = (int) event.getRawX();
            previousY = (int) event.getRawY();
          }
        }).flatMap(new Function<Pair<View, MotionEvent>, Observable<Pair<View, MotionEvent>>>() {
          @Override
          public Observable<Pair<View, MotionEvent>> apply(final Pair<View, MotionEvent> pair) {

            return Observable.just(pair).map(new Function<Pair<View, MotionEvent>, View>() {
              @Override public View apply(Pair<View, MotionEvent> pair) {
                return pair.first;
              }
            }).map(new Function<View, Pair<Integer, Integer>>() {
              // convert to delta amounts between origin and current position
              @Override public Pair<Integer, Integer> apply(View rootView) {

                int deltaX = defaultLeft - rootView.getLeft();
                int deltaY = defaultTop - rootView.getTop();

                return Pair.create(deltaX, deltaY);
              }
            }).doOnNext(new Consumer<Pair<Integer, Integer>>() {
              // call back moved delta amount
              @Override public void accept(Pair<Integer, Integer> deltaXYPair) {
                float percentageX = deltaXYPair.first / DISMISS_THRESHOLD;
                float percentageY = deltaXYPair.second / DISMISS_THRESHOLD;
                onFlicking(-percentageX, percentageY);
              }
            }).map(new Function<Pair<Integer, Integer>, Pair<View, MotionEvent>>() {
              @Override
              public Pair<View, MotionEvent> apply(Pair<Integer, Integer> integerIntegerPair) {
                return pair;
              }
            });
          }
        }).filter(new Predicate<Pair<View, MotionEvent>>() {
          @Override public boolean test(Pair<View, MotionEvent> pair) {
            return pair.second.getAction() == MotionEvent.ACTION_UP;
          }
        }).flatMap(new Function<Pair<View, MotionEvent>, Observable<Pair<View, MotionEvent>>>() {
          // check delta amounts
          @Override public Observable<Pair<View, MotionEvent>> apply(
              final Pair<View, MotionEvent> pair) {

            return Observable.just(pair).map(new Function<Pair<View, MotionEvent>, View>() {
              @Override public View apply(Pair<View, MotionEvent> pair) {
                return pair.first;
              }
            }).map(new Function<View, Pair<Integer, Integer>>() {
              // convert to delta amounts between origin and current position
              @Override public Pair<Integer, Integer> apply(View rootView) {

                int deltaX = defaultLeft - rootView.getLeft();
                int deltaY = defaultTop - rootView.getTop();

                return Pair.create(deltaX, deltaY);
              }
            }).flatMap(new Function<Pair<Integer, Integer>, Observable<Pair<View, MotionEvent>>>() {
              @Override public Observable<Pair<View, MotionEvent>> apply(
                  final Pair<Integer, Integer> deltaXYPair) {
                // judge if flicking amount is over dismiss threshold
                if (Math.abs(deltaXYPair.first) > DISMISS_THRESHOLD
                    || Math.abs(deltaXYPair.second) > DISMISS_THRESHOLD) {
                  // flicking amount is over threshold
                  // -> streams go below to animate throwing
                  return Observable.just(deltaXYPair)
                      .map(new Function<Pair<Integer, Integer>, Pair<View, MotionEvent>>() {
                        @Override public Pair<View, MotionEvent> apply(
                            Pair<Integer, Integer> integerIntegerPair) {
                          return pair;
                        }
                      });
                } else {
                  // back to original dialog position with animation
                  // -> streams is terminated with animate back to origin
                  final int originBackAnimationDuration = 300;

                  return Observable.just(deltaXYPair)
                      .doOnNext(new Consumer<Pair<Integer, Integer>>() {
                        @Override public void accept(Pair<Integer, Integer> deltaXYPair) {

                          PropertyValuesHolder horizontalAnimation =
                              PropertyValuesHolder.ofFloat("translationX", deltaXYPair.first);
                          PropertyValuesHolder verticalAnimation =
                              PropertyValuesHolder.ofFloat("translationY", deltaXYPair.second);
                          PropertyValuesHolder rotateAnimation =
                              PropertyValuesHolder.ofFloat("rotation", 0f);

                          ObjectAnimator originBackAnimation =
                              ObjectAnimator.ofPropertyValuesHolder(pair.first, horizontalAnimation,
                                  verticalAnimation, rotateAnimation);

                          originBackAnimation.setInterpolator(
                              new AccelerateDecelerateInterpolator());

                          originBackAnimation.setDuration(originBackAnimationDuration);

                          originBackAnimation.start();
                        }
                      })
                      .flatMap(new Function<Pair<Integer, Integer>, Observable<?>>() {
                        @Override
                        public Observable<?> apply(Pair<Integer, Integer> integerIntegerPair) {
                          return Observable.just(1)
                              .delay(originBackAnimationDuration, TimeUnit.MILLISECONDS)
                              .observeOn(mainThread());
                        }
                      })
                      .doOnNext(new Consumer<Object>() {
                        @Override public void accept(Object o) {
                          onOriginBack();
                        }
                      })
                      .flatMap(new Function<Object, Observable<Pair<View, MotionEvent>>>() {
                        @Override public Observable<Pair<View, MotionEvent>> apply(Object o) {
                          return Observable.empty();
                        }
                      });
                }
              }
            });
          }
        }).flatMap(new Function<Pair<View, MotionEvent>, Observable<Pair<View, MotionEvent>>>() {
          @Override
          public Observable<Pair<View, MotionEvent>> apply(final Pair<View, MotionEvent> pair) {
            // create and start throwing animation
            return Observable.just(pair.first).map(new Function<View, Pair<Integer, Integer>>() {
              // convert to delta amounts between origin and current position
              @Override public Pair<Integer, Integer> apply(View rootView) {

                int deltaX = defaultLeft - rootView.getLeft();
                int deltaY = defaultTop - rootView.getTop();

                return Pair.create(deltaX, deltaY);
              }
            }).flatMap(new Function<Pair<Integer, Integer>, Observable<Pair<View, MotionEvent>>>() {
              @Override public Observable<Pair<View, MotionEvent>> apply(
                  Pair<Integer, Integer> integerIntegerPair) {
                // make and start throwing animation
                return Observable.zip(Observable.just(integerIntegerPair)
                        .map(new Function<Pair<Integer, Integer>, PropertyValuesHolder>() {
                          @Override
                          public PropertyValuesHolder apply(Pair<Integer, Integer> deltaXYPair) {
                            // make rotate animation
                            float rotation;
                            if (touchedTopArea) {
                              rotation = deltaXYPair.first / DISMISS_THRESHOLD * 540f;
                            } else {
                              rotation = deltaXYPair.first / DISMISS_THRESHOLD * -540f;
                            }
                            PropertyValuesHolder rotateAnimation =
                                PropertyValuesHolder.ofFloat("rotation", rotation);

                            return rotateAnimation;
                          }
                        }), Observable.just(integerIntegerPair)
                        .map(
                            new Function<Pair<Integer, Integer>, Pair<PropertyValuesHolder, PropertyValuesHolder>>() {
                              @Override public Pair<PropertyValuesHolder, PropertyValuesHolder> apply(
                                  Pair<Integer, Integer> deltaXYPair) {
                                // make position transit animation
                                PropertyValuesHolder horizontalAnimation =
                                    PropertyValuesHolder.ofFloat("translationX",
                                        -10 * deltaXYPair.first);

                                PropertyValuesHolder verticalAnimation =
                                    PropertyValuesHolder.ofFloat("translationY",
                                        -10 * deltaXYPair.second);

                                return Pair.create(horizontalAnimation, verticalAnimation);
                              }
                            }), Observable.just(integerIntegerPair)
                        .map(new Function<Pair<Integer, Integer>, ObjectAnimator>() {
                          @Override
                          public ObjectAnimator apply(Pair<Integer, Integer> integerIntegerPair) {
                            // make background alpha transit animation
                            ObjectAnimator alphaAnimation =
                                ObjectAnimator.ofFloat(pair.first.getRootView(), "alpha", 1f, 0f);
                            alphaAnimation.setDuration(400);

                            return alphaAnimation;
                          }
                        }),
                    new Function3<PropertyValuesHolder, Pair<PropertyValuesHolder, PropertyValuesHolder>, ObjectAnimator, Pair<View, MotionEvent>>() {
                      @Override
                      public Pair<View, MotionEvent> apply(PropertyValuesHolder propertyValuesHolder,
                          Pair<PropertyValuesHolder, PropertyValuesHolder> propertyValuesHolderPropertyValuesHolderPair,
                          ObjectAnimator alphaAnimation) {
                        // zip and do animation

                        ObjectAnimator throwingAnimation =
                            ObjectAnimator.ofPropertyValuesHolder(pair.first, propertyValuesHolder,
                                propertyValuesHolderPropertyValuesHolderPair.first,
                                propertyValuesHolderPropertyValuesHolderPair.second);
                        throwingAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
                        throwingAnimation.setDuration(400);

                        alphaAnimation.setDuration(400);

                        throwingAnimation.start();
                        alphaAnimation.start();

                        return pair;
                      }
                    });
              }
            });
          }
        }).flatMap(new Function<Pair<View, MotionEvent>, Observable<Pair<View, MotionEvent>>>() {
          // waiting animation end
          @Override public Observable<Pair<View, MotionEvent>> apply(
              Pair<View, MotionEvent> viewMotionEventPair) {

            return Observable.just(viewMotionEventPair)
                .delay(400, TimeUnit.MILLISECONDS)
                .observeOn(mainThread());
          }
        }).flatMap(new Function<Pair<View, MotionEvent>, Observable<Pair<View, MotionEvent>>>() {
          @Override public Observable<Pair<View, MotionEvent>> apply(
              final Pair<View, MotionEvent> viewMotionEventPair) {

            return Observable.just(viewMotionEventPair.first)
                .map(new Function<View, Pair<Integer, Integer>>() {
                  // convert to delta amounts between origin and current position
                  @Override public Pair<Integer, Integer> apply(View rootView) {

                    int deltaX = defaultLeft - rootView.getLeft();
                    int deltaY = defaultTop - rootView.getTop();

                    return Pair.create(deltaX, deltaY);
                  }
                })
                .doOnNext(new Consumer<Pair<Integer, Integer>>() {
                  // call back X direction
                  @Override public void accept(Pair<Integer, Integer> integerIntegerPair) {
                    if (onFlickedXDirectionListener != null) {
                      if (integerIntegerPair.first > 0) {
                        if (integerIntegerPair.second < 0) {
                          onFlickedXDirectionListener.onFlickableDialogFlicked(FlickableDialogListener.X_DIRECTION.LEFT_BOTTOM);
                        } else {
                          onFlickedXDirectionListener.onFlickableDialogFlicked(
                              FlickableDialogListener.X_DIRECTION.LEFT_TOP);
                        }
                      } else {
                        if (integerIntegerPair.second < 0) {
                          onFlickedXDirectionListener.onFlickableDialogFlicked(
                              FlickableDialogListener.X_DIRECTION.RIGHT_BOTTOM);
                        } else {
                          onFlickedXDirectionListener.onFlickableDialogFlicked(
                              FlickableDialogListener.X_DIRECTION.RIGHT_TOP);
                        }
                      }
                    }
                  }
                })
                .map(new Function<Pair<Integer, Integer>, Pair<View, MotionEvent>>() {
                  @Override
                  public Pair<View, MotionEvent> apply(Pair<Integer, Integer> integerIntegerPair) {
                    return viewMotionEventPair;
                  }
                });
          }
        }).doOnSubscribe(new Consumer<Disposable>() {
          @Override public void accept(Disposable disposable) {

            ObjectAnimator alphaAnimation = ObjectAnimator.ofFloat(frameLayout, "alpha", 0f, 1f);
            alphaAnimation.setDuration(200);
            alphaAnimation.start();
          }
        }).subscribe(new Consumer<Pair<View, MotionEvent>>() {
          @Override public void accept(Pair<View, MotionEvent> view) {
            dismiss();
          }
        }));

    Dialog dialog =
        new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
    dialog.setContentView(frameLayout);
    dialog.setCancelable(cancelable);
    dialog.setOnCancelListener(new Dialog.OnCancelListener() {
      @Override public void onCancel(DialogInterface dialog) {

        if(onFlickableDialogCanceled != null){
          onFlickableDialogCanceled.onFlickableDialogCanceled();
        }
      }
    });

    return dialog;
  }


  @Nullable private FlickableDialogListener.OnFlickedXDirection onFlickedXDirectionListener;

  @Nullable private FlickableDialogListener.OnCanceled onFlickableDialogCanceled;

  public void setOnFlick(FlickableDialogListener.OnFlickedXDirection onFlickedXDirectionListener) {
    this.onFlickedXDirectionListener = onFlickedXDirectionListener;
  }

  public void setOnCancel(FlickableDialogListener.OnCanceled onFlickableDialogCanceled) {
    this.onFlickableDialogCanceled = onFlickableDialogCanceled;
  }

  public void setCanceledOnTouchOutside(boolean cancel) {
    this.cancelAndDismissTaken = cancel;
  }

  public void setCancelable(boolean flag) {
    this.cancelable = flag;
  }

  @Override public void onDetach() {

    compositeSubscription.dispose();

    onFlickedXDirectionListener = null;
    onFlickableDialogCanceled = null;

    super.onDetach();
  }

  @Override public void onDismiss(DialogInterface dialogInterface) {

    compositeSubscription.dispose();

    onFlickedXDirectionListener = null;
    onFlickableDialogCanceled = null;

    super.onDismiss(dialogInterface);
  }
}