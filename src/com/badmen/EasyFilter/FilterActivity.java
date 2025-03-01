package com.badmen.EasyFilter;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.SeekBar;
import com.utilsframework.android.bitmap.BitmapUtilities;
import com.utilsframework.android.bitmap.Size;
import com.utilsframework.android.file.FileUtils;
import com.utilsframework.android.social.SocialUtils;
import com.utilsframework.android.subscaleview.ScaleImagePreviewActivity;
import com.utilsframework.android.threading.AsyncOperationCallback;
import com.utilsframework.android.view.Alerts;
import com.utilsframework.android.view.OnYes;
import com.utilsframework.android.view.UiMessages;
import jp.co.cyberagent.android.gpuimage.*;

import java.io.File;
import java.util.List;

/**
 * Created by semyon.tikhonenko on 22.05.2015.
 */
public class FilterActivity extends Activity {
    private static final String IMAGE_PATH = "imagePath";
    private static final boolean GENERATE_SAMPLES = false;

    private String imagePath;
    private GPUImageView image;
    private List<Filter> filters;
    private GPUImageFilterTools.FilterAdjuster filterAdjuster;
    private SeekBar seekBar;
    private FilterGroupManager filterGroup = new FilterGroupManager();

    public static void start(Context context, String imagePath) {
        Intent intent = new Intent(context, FilterActivity.class);
        intent.putExtra(IMAGE_PATH, imagePath);
        context.startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.filter, menu);

        menu.findItem(R.id.saveAs).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                showSaveAsAlert();
                return true;
            }
        });

        menu.findItem(R.id.save).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                saveImage();
                return true;
            }
        });

        menu.findItem(R.id.action_undo).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                GPUImageFilter filter = filterGroup.undo();
                updateFilter(filterGroup.getTopFilter(), filter);
                return true;
            }
        });

        menu.findItem(R.id.action_redo).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                GPUImageFilter filter = filterGroup.redo();
                updateFilter(filterGroup.getTopFilter(), filter);
                return true;
            }
        });

        menu.findItem(R.id.publishOnInstagram).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                SocialUtils.postOnInstagram(FilterActivity.this, imagePath);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imagePath = getIntent().getStringExtra(IMAGE_PATH);
        Alerts.runAsyncOperationWithCircleLoading(this, R.string.please_wait,
                new AsyncOperationCallback<Bitmap>() {

                    @Override
                    public Bitmap runOnBackground() {
                        filters = GPUImageFilterTools.getFilters(FilterActivity.this);
                        return BitmapFactory.decodeFile(imagePath);
                    }

                    @Override
                    public void onFinish(Bitmap result) {
                        initViews(result);
                    }
                });
    }

    private void updateAdjuster(GPUImageFilter filter) {
        filterAdjuster = new GPUImageFilterTools.FilterAdjuster(filter);
        seekBar.setVisibility(filterAdjuster.canAdjust() ? View.VISIBLE : View.INVISIBLE);
        if (filterAdjuster.canAdjust()) {
            int progress = filterAdjuster.getProgress();
            seekBar.setProgress(progress);
            adjustFilter(progress);
        }
    }

    private void initViews(Bitmap bitmap) {
        setContentView(R.layout.filter_activity);

        image = (GPUImageView) findViewById(R.id.image);
        if (GENERATE_SAMPLES) {
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sample);
        }

        image.setImage(bitmap);

        if (!GENERATE_SAMPLES) {
            initFilterEditing();
        } else {
            generateSamples();
        }
    }

    private void initFilterEditing() {
        seekBar = (SeekBar) findViewById(R.id.adjuster);
        seekBar.setMax(100);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                adjustFilter(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final GridView filtersView = (GridView) findViewById(R.id.grid);
        FilterAdapter filterAdapter = new FilterAdapter(this);

        filterAdapter.setElements(filters);
        filtersView.setAdapter(filterAdapter);

        filtersView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Filter filter = filters.get(position);
                GPUImageFilter gpuImageFilter = filterGroup.addOrReplaceFilter(filter.filter);
                updateFilter(filter.filter, gpuImageFilter);
            }
        });

        findViewById(R.id.preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveImageForPreview();
            }
        });

        findViewById(R.id.apply_filter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterGroup.applyFilter();
                UiMessages.message(FilterActivity.this, R.string.filter_applies_message);
            }
        });
    }

    private void adjustFilter(int progress) {
        if (filterAdjuster != null) {
            filterAdjuster.adjust(progress);
            image.requestRender();
        }
    }

    private void updateFilter(GPUImageFilter topFilter, GPUImageFilter groupFilter) {
        image.setFilter(groupFilter);
        updateAdjuster(topFilter);
        image.requestRender();
    }

    private void showSaveAsAlert() {
        Alerts.InputAlertSettings settings = new Alerts.InputAlertSettings();
        settings.message = getString(R.string.enter_file_name);
        settings.okButtonText = getString(R.string.save);
        settings.onInputOk = new Alerts.OnInputOk() {
            @Override
            public void onOk(String value) {
                saveImageAs(value + ".jpg");
            }
        };
        Alerts.showAlertWithInput(this, settings);
    }

    private void saveImageAs(String fileName) {
        saveImageAs(fileName, true);
    }

    private void saveImage() {
        Alerts.YesNoAlertSettings settings = new Alerts.YesNoAlertSettings();
        settings.message = getString(R.string.save_image_confirm);
        settings.noButtonText = getString(R.string.cancel);
        settings.yesButtonText = getString(R.string.save);

        settings.onYes = new OnYes() {
            @Override
            public void onYes() {
                saveImageAs(imagePath, false);
            }
        };

        Alerts.showYesNoAlert(this, settings);
    }

    private void saveImageForPreview() {
        File tempFile = FileUtils.createTempFile(this, "temp.jpg");
        saveImageAs(tempFile.getAbsolutePath(), false, true);
    }

    private void saveImageAs(final String fileName, boolean toPictures) {
        saveImageAs(fileName, toPictures, false);
    }

    private void saveImageAs(final String fileName, boolean toPictures, final boolean forPreview) {
        String message = getString(forPreview ? R.string.generating_preview : R.string.saving_image);
        final ProgressDialog progressDialog = Alerts.showCircleProgressDialog(this, message);
        Size size = BitmapUtilities.getBitmapDimensions(imagePath);
        GPUImageView.OnPictureSavedListener listener = new GPUImageView.OnPictureSavedListener() {
            @Override
            public void onPictureSaved(Uri uri, Bitmap bitmap) {
                progressDialog.dismiss();
                if (!forPreview) {
                    ScaleImagePreviewActivity.start(FilterActivity.this, uri);
                } else {
                    ScaleImagePreviewActivity.start(FilterActivity.this, fileName, true);
                }
                imagePath = BitmapUtilities.getFromMediaUri(getContentResolver(), uri).getAbsolutePath();
                image.setImage(bitmap);
                GPUImageFilter filter = new GPUImageFilter();
                updateFilter(filter, filter);
            }
        };
        if (toPictures) {
            image.saveToPictures("Easy Filter", fileName,
                    size.width, size.height, listener);
        } else {
            image.saveImage(fileName, size.width, size.height, listener);
        }
    }

    void generateSamples() {
        new Runnable() {
            int i = 0;

            @Override
            public void run() {
                if (i < filters.size()) {
                    Filter filter = filters.get(i);
                    image.setFilter(filter.filter);
                    image.saveToPictures("samples", "sample_" + i + ".jpg", 100, 100,
                            new GPUImageView.OnPictureSavedListener() {
                                @Override
                                public void onPictureSaved(Uri uri, Bitmap bitmap) {
                                    i++;
                                    run();
                                }
                            });
                } else {
                    UiMessages.message(FilterActivity.this, "finished");
                }
            }
        }.run();
    }
}
