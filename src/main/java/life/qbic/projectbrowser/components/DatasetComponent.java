/*******************************************************************************
 * QBiC Project qNavigator enables users to manage their projects. Copyright (C) "2016” Christopher
 * Mohr, David Wojnar, Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package life.qbic.projectbrowser.components;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.project.Project;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.sample.Sample;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.data.util.HierarchicalContainer;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.server.*;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;

import life.qbic.portal.portlet.ProjectBrowserPortlet;
import life.qbic.portal.utils.PortalUtils;
import life.qbic.projectbrowser.controllers.DataHandler;
import life.qbic.projectbrowser.controllers.State;
import life.qbic.projectbrowser.helpers.*;
import life.qbic.projectbrowser.model.DatasetBean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tepi.filtertable.FilterTreeTable;

import javax.portlet.PortletSession;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Displays the datasets.
 */
public class DatasetComponent extends CustomComponent {

    private final static long serialVersionUID = 8672873911284888801L;
    private final static Logger LOG = LogManager.getLogger(DatasetComponent.class);
    private final static String DOWNLOAD_BUTTON_CAPTION = "Download";
    private final static String[] FILTER_TABLE_COLUMNS = new String[]{"Select", "File Name", "Description", "Dataset Type", "Registration Date", "File Size"};

    private final VerticalLayout mainLayout;
    private final FilterTreeTable table;
    private final VerticalLayout verticalLayout;
    private final DataHandler datahandler;
    private final ButtonLink download;
    private final Button tsvExportButton;
    private final UglyToPrettyNameMapper prettyNameMapper;
    private final Label headerLabel;

    private HierarchicalContainer datasets;
    private int numberOfDatasets;
    private volatile FileDownloader fileDownloader;

    public DatasetComponent(DataHandler dh, State state, String resourceurl) {
        this.datahandler = dh;

        this.setCaption("Datasets");

        download = new ButtonLink(DOWNLOAD_BUTTON_CAPTION, new ExternalResource(""));
        tsvExportButton = new Button("Export as TSV");
        prettyNameMapper = new UglyToPrettyNameMapper();
        headerLabel = new Label("", ContentMode.HTML);
        verticalLayout = new VerticalLayout();
        mainLayout = new VerticalLayout(verticalLayout);
        table = buildFilterTable();

        this.initUI();
    }

    private void initUI() {
        mainLayout.setResponsive(true);
        this.setCompositionRoot(mainLayout);
    }

    public void updateUI(String type, String id) {

        if (id == null) {
            return;
        }
        try {
            HierarchicalContainer datasetContainer = new HierarchicalContainer();
            datasetContainer.addContainerProperty("Select", CheckBox.class, null);
            datasetContainer.addContainerProperty("Project", String.class, null);
            datasetContainer.addContainerProperty("Sample", String.class, null);
            datasetContainer.addContainerProperty("Description", String.class, null);
            datasetContainer.addContainerProperty("File Name", String.class, null);
            datasetContainer.addContainerProperty("File Type", String.class, null);
            datasetContainer.addContainerProperty("Dataset Type", String.class, null);
            datasetContainer.addContainerProperty("Registration Date", String.class, null);
            datasetContainer.addContainerProperty("Validated", Boolean.class, null);
            datasetContainer.addContainerProperty("File Size", String.class, null);
            datasetContainer.addContainerProperty("file_size_bytes", Long.class, null);
            datasetContainer.addContainerProperty("dl_link", String.class, null);
            datasetContainer.addContainerProperty("isDirectory", Boolean.class, null);
            datasetContainer.addContainerProperty("CODE", String.class, null);

            List<DataSet> retrievedDatasets = null;

            // clear download queue for new view
            PortletSession portletSession = ((ProjectBrowserPortlet) UI.getCurrent()).getPortletSession();
            portletSession.setAttribute("qbic_download",
                new HashMap<String, SimpleEntry<String, Long>>(),
                PortletSession.APPLICATION_SCOPE);

            Map<String, Sample> checkedTestSamples = new HashMap<>();

            switch (type) {
                case "project":
                    String projectIdentifier = id;
                    retrievedDatasets =
                            datahandler.getOpenBisClient().getDataSetsOfProject(projectIdentifier);
                    List<Sample> allSamples =
                            datahandler.getOpenBisClient().getSamplesOfProject(projectIdentifier);

                    for (Sample sample : allSamples) {
                        checkedTestSamples.put(sample.getCode(), sample);
                    }
                    break;

                case "experiment":
                    String experimentIdentifier = id;
                    retrievedDatasets =
                            datahandler.getOpenBisClient().getDataSetsOfExperimentByIdentifier(experimentIdentifier);
                    Project proj =
                            datahandler.getOpenBisClient().getProjectOfExperimentByIdentifier(experimentIdentifier);
                    List<Sample> extSamples =
                            datahandler.getOpenBisClient().getSamplesOfProject(proj.getIdentifier().toString());

                    for (Sample sample : extSamples) {
                        checkedTestSamples.put(sample.getCode(), sample);
                    }
                    break;

                case "sample":
                    String sampleIdentifier = id;
                    retrievedDatasets = new ArrayList<>();

                    Sample start =
                        datahandler.getOpenBisClient().getSampleWithParentsAndChildren(sampleIdentifier);

                    Project sampProject =
                            datahandler.getOpenBisClient().getProject(start.getProject().getIdentifier().toString());

                    //Project sampProject = datahandler.getOpenBisClient()
                    //   .getProjectOfExperimentByIdentifier(start.getExperimentIdentifierOrNull());

                    retrievedDatasets.addAll(datahandler.getOpenBisClient().getDataSetsOfSample(start.getCode()));

                    List<Sample> allProjSamples =
                            datahandler.getOpenBisClient().getSamplesOfProject(sampProject.getIdentifier().toString());

                    for (Sample sample : allProjSamples) {
                        checkedTestSamples.put(sample.getCode(), sample);
                    }

                    Set<Sample> startList = new HashSet<>();
                    Set<Sample> allChildren = getAllChildren(startList, start);

                    for (Sample samp : allChildren) {
                        retrievedDatasets.addAll(datahandler.getOpenBisClient().getDataSetsOfSample(samp.getCode()));
                    }
                    break;

                default:
                    retrievedDatasets = new ArrayList<>();
                    break;
            }

            numberOfDatasets = retrievedDatasets.size();

            final BeanItemContainer<DatasetBean> forExport = new BeanItemContainer(DatasetBean.class);
            if (numberOfDatasets == 0) {

                if (type.equals("project")) {
                    headerLabel.setValue(String.format(
                        "This view shows all datasets associated with this project. There are %s registered datasets.",
                        numberOfDatasets));

                    Utils.Notification("No datasets available.",
                        "No data is available for this project. Please contact the project manager if this is not expected.",
                        "warning");
                } else {
                    headerLabel.setValue(String.format(
                        "This view shows all datasets associated with this sample (including samples which have been derived from this sample). There are %s registered datasets.",
                        numberOfDatasets));

                    Utils.Notification("No datasets available.",
                        "No data is connected to this sample. Please contact the project manager if this is not expected.",
                        "warning");
                }
            } else {

                Map<String, String> samples = new HashMap<>();

                // project same for all datasets
                String projectCode =
                        retrievedDatasets.get(0).getExperiment().getIdentifier().toString().split("/")[2];

                for (DataSet dataset : retrievedDatasets) {
                    samples.put(dataset.getCode(), dataset.getSample().getCode());
                }

                List<DatasetBean> dsBeans = datahandler.queryDatasetsForFolderStructure(retrievedDatasets);

                numberOfDatasets = dsBeans.size();

                if (type.equals("project")) {
                    headerLabel.setValue(String.format(
                        "This view shows all datasets associated with this project. There are %s registered datasets.",
                        numberOfDatasets));
                } else if (type.equals("sample")) {
                    headerLabel.setValue(String.format(
                        "This view shows all datasets associated with this sample (including samples which have been derived from this sample). There are %s registered datasets.",
                        numberOfDatasets));
                }

                for (DatasetBean d : dsBeans) {
                    Date date = d.getRegistrationDate();
                    SimpleDateFormat sd = new SimpleDateFormat("yyyy-MM-dd hh:mm a");
                    String dateString = sd.format(date);
                    String sampleID = samples.get(d.getCode());

                    Sample dsSample = checkedTestSamples.get(sampleID);

                    String secNameDS = d.getProperties().get("Q_SECONDARY_NAME");
                    LOG.info("DataSetBean:                 " + d.getCode());
                    LOG.info("DataSet Sample:              " + dsSample.getCode() );
                    LOG.info("DataSetBean Q_SECONDARY_NAME:" + secNameDS);

                    String secName = datahandler.getSecondaryName(dsSample, secNameDS);

                    forExport.addBean(d);

                    registerDatasetInTable(d, datasetContainer, projectCode, sampleID, dateString, null, secName);
                }
            }

            this.setContainerDataSource(datasetContainer);
            prepareTSVExportFile(forExport, id);

        } catch (Exception e) {
            LOG.error(String.format("getting dataset failed for dataset %s %s", type, id), e);
        }
    }

    private synchronized void prepareTSVExportFile(final BeanItemContainer<DatasetBean> itemContainer, final String id) {
        // disable tsv export button (it will be enabled by the background thread preparing the export)
        tsvExportButton.setEnabled(false);
        tsvExportButton.setDescription("Please wait a moment, your TSV export data is being prepared in the background.");
        // remove the button from the downloader
        if (fileDownloader != null) {
            tsvExportButton.removeExtension(fileDownloader);
        }
        UI.getCurrent().setPollInterval(150);
        CompletableFuture.supplyAsync(() -> Utils.containerToString(itemContainer))
            .thenApplyAsync(tsvContent -> Utils.getTSVStream(tsvContent, String.format("%s_%s_", id.substring(1).replace("/", "_"), "registered_datasets")))
            .thenAccept((streamResource) -> {
                // update UI
                UI.getCurrent().access(() -> {
                    fileDownloader = new FileDownloader(streamResource);
                    fileDownloader.extend(tsvExportButton);
                    tsvExportButton.setEnabled(true);
                    tsvExportButton.setDescription("Click to download");
                });
                UI.getCurrent().setPollInterval(-1);
            });
    }

    public void setContainerDataSource(HierarchicalContainer newDataSource) {
        datasets = (HierarchicalContainer) newDataSource;
        table.setContainerDataSource(this.datasets);
        table.setPageLength(Math.max(3, Math.min(numberOfDatasets, 10)));

        table.setVisibleColumns((Object[]) FILTER_TABLE_COLUMNS);

        table.setSizeFull();
        Object[] sorting = {"Registration Date"};
        boolean[] order = {false};
        table.sort(sorting, order);
        this.buildLayout();
    }

    /**
     * Precondition: {DatasetView#table} has to be initialized. e.g. with {DatasetView#buildFilterTable} If it is not, strange behaviour has to be expected.
     * builds the Layout of this view.
     */
    private void buildLayout() {
        this.verticalLayout.removeAllComponents();
        this.verticalLayout.setSizeFull();

        verticalLayout.setResponsive(true);

        // Table (containing datasets) section
        VerticalLayout tableSection = new VerticalLayout();
        HorizontalLayout tableSectionContent = new HorizontalLayout();
        tableSection.setResponsive(true);
        tableSectionContent.setResponsive(true);

        // tableSectionContent.setCaption("Datasets");
        // tableSectionContent.setIcon(FontAwesome.FLASK);
        // tableSection.addComponent(new Label(String.format("This project contains %s dataset(s).",
        // numberOfDatasets)));
        tableSectionContent.setMargin(new MarginInfo(true, false, true, false));

        tableSection.addComponent(headerLabel);
        tableSectionContent.addComponent(this.table);
        verticalLayout.setMargin(new MarginInfo(false, true, false, false));

        tableSection.setMargin(new MarginInfo(true, false, false, true));
        // tableSectionContent.setMargin(true);
        // tableSection.setMargin(true);

        tableSection.addComponent(tableSectionContent);
        this.verticalLayout.addComponent(tableSection);

        table.setSizeFull();
        tableSection.setSizeFull();
        tableSectionContent.setSizeFull();

        // this.table.setSizeFull();

        HorizontalLayout buttonLayout = new HorizontalLayout();
        buttonLayout.setMargin(new MarginInfo(false, false, true, true));
        buttonLayout.setHeight(null);
        // buttonLayout.setWidth("100%");
        buttonLayout.setSpacing(true);
        buttonLayout.setResponsive(true);

        // final Button visualize = new Button(VISUALIZE_BUTTON_CAPTION);

        Button checkAll = new Button("Select all datasets");
        checkAll.addClickListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                for (Object itemId : table.getItemIds()) {
                    ((CheckBox) table.getItem(itemId).getItemProperty("Select").getValue()).setValue(true);
                }
            }
        });

        Button uncheckAll = new Button("Unselect all datasets");
        uncheckAll.addClickListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                for (Object itemId : table.getItemIds()) {
                    ((CheckBox) table.getItem(itemId).getItemProperty("Select").getValue()).setValue(false);
                }
            }
        });

        String content =
            "<p> In case of multiple file selections, Project Browser will create a tar archive.</p>"
                + "<hr>"
                + "<p> If you need help on extracting a tar archive file, follow the tips below: </p>"
                + "<p>" + FontAwesome.WINDOWS.getHtml() + " Windows </p>"
                + "<p> To open/extract TAR file on Windows, you can use 7-Zip, Easy 7-Zip, PeaZip.</p>"
                + "<hr>" + "<p>" + FontAwesome.APPLE.getHtml() + " MacOS </p>"
                + "<p> To open/extract TAR file on Mac, you can use Mac OS built-in utility Archive Utility,<br> or third-party freeware. </p>"
                + "<hr>" + "<p>" + FontAwesome.LINUX.getHtml() + " Linux </p>"
                + "<p> You need to use command tar. The tar is the GNU version of tar archiving utility. <br> "
                + "To extract/unpack a tar file, type: $ tar -xvf file.tar</p>";

        tsvExportButton.setIcon(FontAwesome.DOWNLOAD);

        PopupView tooltip = new PopupView(new ToolTip(content));
        tooltip.setHeight("44px");

        HorizontalLayout help = new HorizontalLayout();
        help.setSizeFull();
        HorizontalLayout helpContent = new HorizontalLayout();
        // helpContent.setSizeFull();

        help.setMargin(new MarginInfo(false, false, false, true));
        Label helpText = new Label("Attention: Click here before Download!");
        helpContent.addComponent(new Label(FontAwesome.QUESTION_CIRCLE.getHtml(), ContentMode.HTML));
        helpContent.addComponent(helpText);
        helpContent.addComponent(tooltip);
        helpContent.setSpacing(true);

        help.addComponent(helpContent);
        help.setComponentAlignment(helpContent, Alignment.TOP_CENTER);

        buttonLayout.addComponent(tsvExportButton);
        buttonLayout.addComponent(checkAll);
        buttonLayout.addComponent(uncheckAll);
        // buttonLayout.addComponent(visualize);
        buttonLayout.addComponent(this.download);

        /**
         * prepare download.
         */
        download.setEnabled(false);
        download.setResource(new ExternalResource("javascript:"));
        // visualize.setEnabled(false);

        for (final Object itemId : this.table.getItemIds()) {
            setCheckedBox(itemId, (String) this.table.getItem(itemId).getItemProperty("CODE").getValue());
        }

        this.table.addItemClickListener(new ItemClickListener() {
            @Override
            public void itemClick(ItemClickEvent event) {
                if (!event.isDoubleClick() & !((boolean) table.getItem(event.getItemId())
                    .getItemProperty("isDirectory").getValue())) {
                    String datasetCode =
                        (String) table.getItem(event.getItemId()).getItemProperty("CODE").getValue();
                    String datasetFileName =
                        (String) table.getItem(event.getItemId()).getItemProperty("File Name").getValue();
                    URL url;
                    try {
                        Resource res = null;
                        Object parent = table.getParent(event.getItemId());
                        if (parent != null) {
                            String parentDatasetFileName =
                                (String) table.getItem(parent).getItemProperty("File Name").getValue();
                            url = datahandler.getOpenBisClient().getUrlForDataset(datasetCode,
                                parentDatasetFileName + "/" + datasetFileName);
                        } else {
                            url = datahandler.getOpenBisClient().getUrlForDataset(datasetCode, datasetFileName);
                        }

                        Window subWindow = new Window();
                        VerticalLayout subContent = new VerticalLayout();
                        subContent.setMargin(true);
                        subContent.setSizeFull();
                        subWindow.setContent(subContent);
                        ProjectBrowserPortlet ui = (ProjectBrowserPortlet) UI.getCurrent();
                        Boolean visualize = false;

                        if (datasetFileName.endsWith(".pdf")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("application/pdf");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".png")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            // streamres.setMIMEType("application/png");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".qcML")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("text/xml");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".alleles")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("text/plain");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".tsv")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("text/plain");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".GSvar")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("text/plain");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".log")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("text/plain");
                            res = streamres;
                            visualize = true;
                        }

                        if (datasetFileName.endsWith(".html")) {
                            QcMlOpenbisSource re = new QcMlOpenbisSource(url);
                            StreamResource streamres = new StreamResource(re, datasetFileName);
                            streamres.setMIMEType("text/html");
                            res = streamres;
                            visualize = true;
                        }

                        if (visualize) {
                            // LOG.debug("Is resource null?: " + String.valueOf(res == null));
                            BrowserFrame frame = new BrowserFrame("", res);

                            subContent.addComponent(frame);

                            // Center it in the browser window
                            subWindow.center();
                            subWindow.setModal(true);
                            subWindow.setSizeUndefined();
                            subWindow.setHeight("75%");
                            subWindow.setWidth("75%");
                            subWindow.setResizable(false);

                            frame.setSizeFull();
                            frame.setHeight("100%");
                            // frame.setHeight((int) (ui.getPage().getBrowserWindowHeight() * 0.9), Unit.PIXELS);

                            // Open it in the UI
                            ui.addWindow(subWindow);
                        }

                    } catch (MalformedURLException e) {
                        LOG.error(String.format(
                            "Visualization failed because of malformedURL for dataset: %s", datasetCode));
                        Notification.show(
                            "Given dataset has no file attached to it!! Please Contact your project manager. Or check whether it already has some data",
                            Notification.Type.ERROR_MESSAGE);
                    }
                }
            }
        });

        this.verticalLayout.addComponent(buttonLayout);
        this.verticalLayout.addComponent(help);

    }

    private void setCheckedBox(Object itemId, String parentFolder) {
        CheckBox itemCheckBox =
            (CheckBox) this.table.getItem(itemId).getItemProperty("Select").getValue();
        itemCheckBox.addValueChangeListener(new TableCheckBoxValueChangeListener(itemId, parentFolder));

        if (table.hasChildren(itemId)) {
            for (Object childId : table.getChildren(itemId)) {
                String newParentFolder = Paths
                    .get(parentFolder,
                        (String) this.table.getItem(itemId).getItemProperty("File Name").getValue())
                    .toString();
                setCheckedBox(childId, newParentFolder);
            }
        }

    }

    private FilterTreeTable buildFilterTable() {
        FilterTreeTable filterTable = new FilterTreeTable();
        filterTable.setSizeFull();

        filterTable.setFilterDecorator(new DatasetViewFilterDecorator());
        filterTable.setFilterGenerator(new DatasetViewFilterGenerator());

        filterTable.setFilterBarVisible(true);

        filterTable.setSelectable(true);
        filterTable.setImmediate(true);
        filterTable.setMultiSelect(true);

        // filterTable.setRowHeaderMode(RowHeaderMode.INDEX);

        filterTable.setColumnCollapsingAllowed(false);

        filterTable.setColumnReorderingAllowed(true);

        if (this.datasets != null) {
            filterTable.setContainerDataSource(this.datasets);
        }

        return filterTable;
    }

    public void registerDatasetInTable(DatasetBean d, HierarchicalContainer dataset_container,
        String project, String sample, String ts, Object parent, String secName) {

        UglyToPrettyNameMapper mapper = new UglyToPrettyNameMapper();

        if (d.hasChildren()) {

            Object new_ds = dataset_container.addItem();

            List<DatasetBean> subList = d.getChildren();

            dataset_container.setChildrenAllowed(new_ds, true);

            dataset_container.getContainerProperty(new_ds, "Select").setValue(new CheckBox());

            dataset_container.getContainerProperty(new_ds, "Project").setValue(project);
            dataset_container.getContainerProperty(new_ds, "Sample").setValue(sample);
            dataset_container.getContainerProperty(new_ds, "Description").setValue(secName);
            // dataset_container.getContainerProperty(new_ds, "Sample Type").setValue(
            // d.getSample().getType());
            dataset_container.getContainerProperty(new_ds, "File Name").setValue(d.getName());
            dataset_container.getContainerProperty(new_ds, "File Type").setValue("Folder");
            dataset_container.getContainerProperty(new_ds, "Dataset Type")
                .setValue(prettyNameMapper.getPrettyName(d.getType()) + " Folder");
            dataset_container.getContainerProperty(new_ds, "Registration Date").setValue(ts);
            dataset_container.getContainerProperty(new_ds, "Validated").setValue(true);
            dataset_container.getContainerProperty(new_ds, "dl_link").setValue(d.getDssPath());
            dataset_container.getContainerProperty(new_ds, "CODE").setValue(d.getCode());
            dataset_container.getContainerProperty(new_ds, "file_size_bytes").setValue(d.getFileSize());
            dataset_container.getContainerProperty(new_ds, "isDirectory").setValue(true);

            if (parent != null) {
                dataset_container.setParent(new_ds, parent);
            }

            // LOG.debug(d+" has files/folders:");
            for (DatasetBean file : subList) {
                // LOG.debug(file.getFileName());
                registerDatasetInTable(file, dataset_container, project, sample, ts, new_ds, secName);
            }

        } else {
            // LOG.debug("(no more subfolders)");
            // sut.println("Now it should be a file: " + filelist[0].getPathInDataSet());

            Object new_file = dataset_container.addItem();
            dataset_container.setChildrenAllowed(new_file, false);
            dataset_container.getContainerProperty(new_file, "Select").setValue(new CheckBox());
            dataset_container.getContainerProperty(new_file, "Project").setValue(project);
            dataset_container.getContainerProperty(new_file, "Sample").setValue(sample);
            dataset_container.getContainerProperty(new_file, "Description").setValue(secName);
            // dataset_container.getContainerProperty(new_file, "Sample Type").setValue(sampleType);
            dataset_container.getContainerProperty(new_file, "File Name").setValue(d.getFileName());
            dataset_container.getContainerProperty(new_file, "File Type").setValue(d.getFileType());
            dataset_container.getContainerProperty(new_file, "Dataset Type")
                .setValue(prettyNameMapper.getPrettyName(d.getType()));
            dataset_container.getContainerProperty(new_file, "Registration Date").setValue(ts);
            dataset_container.getContainerProperty(new_file, "Validated").setValue(true);
            dataset_container.getContainerProperty(new_file, "File Size")
                .setValue(PortalUtils.humanReadableByteCount(d.getFileSize(), true));
            dataset_container.getContainerProperty(new_file, "dl_link").setValue(d.getDssPath());
            dataset_container.getContainerProperty(new_file, "CODE").setValue(d.getCode());
            dataset_container.getContainerProperty(new_file, "file_size_bytes").setValue(d.getFileSize());
            dataset_container.getContainerProperty(new_file, "isDirectory").setValue(false);
            if (parent != null) {
                dataset_container.setParent(new_file, parent);
            }
        }
    }

    private class TableCheckBoxValueChangeListener implements ValueChangeListener {

        /**
         *
         */
        private static final long serialVersionUID = -7177199525909283879L;
        private Object itemId;
        private String itemFolderName;

        public TableCheckBoxValueChangeListener(final Object itemId, String itemFolderName) {
            this.itemFolderName = itemFolderName;
            this.itemId = itemId;
        }

        @Override
        public void valueChange(ValueChangeEvent event) {

            PortletSession portletSession = ((ProjectBrowserPortlet) UI.getCurrent()).getPortletSession();

            Map<String, SimpleEntry<String, Long>> entries =
                (Map<String, SimpleEntry<String, Long>>) portletSession
                    .getAttribute("qbic_download", PortletSession.APPLICATION_SCOPE);

            boolean itemSelected = (Boolean) event.getProperty().getValue();
            /*
             * String fileName = ""; Object parentId = table.getParent(itemId); //In order to prevent
             * infinity loop int folderDepth = 0; while(parentId != null && folderDepth < 100){ fileName =
             * Paths.get((String) table.getItem(parentId).getItemProperty("File Name").getValue(),
             * fileName).toString(); parentId = table.getParent(parentId); folderDepth++; }
             */

            valueChange(itemId, itemSelected, entries, itemFolderName);
            portletSession.setAttribute("qbic_download", entries, PortletSession.APPLICATION_SCOPE);

            if (entries == null || entries.isEmpty()) {
                download.setResource(new ExternalResource("javascript:"));
                download.setEnabled(false);
            } else {
                String resourceUrl =
                    (String) portletSession.getAttribute("resURL", PortletSession.APPLICATION_SCOPE);
                download.setResource(new ExternalResource(resourceUrl));
                download.setEnabled(true);
            }

        }

        /**
         * updates entries (puts and removes) for selected table item and all its children. Means Checkbox is updated. And in case download button is clicked
         * all checked items will be downloaded.
         *
         * @param itemId Container id
         * @param itemSelected checkbox value of the item
         * @param entries all checked items
         * @param fileName fileName of current item
         */
        private void valueChange(Object itemId, boolean itemSelected,
            Map<String, SimpleEntry<String, Long>> entries, String fileName) {

            ((CheckBox) table.getItem(itemId).getItemProperty("Select").getValue())
                .setValue(itemSelected);
            fileName = Paths
                .get(fileName, (String) table.getItem(itemId).getItemProperty("File Name").getValue())
                .toString();

            // System.out.println(fileName);
            if (table.hasChildren(itemId)) {
                for (Object childId : table.getChildren(itemId)) {
                    valueChange(childId, itemSelected, entries, fileName);
                }
            } else if (itemSelected) {
                String datasetCode = (String) table.getItem(itemId).getItemProperty("CODE").getValue();

                Long datasetFileSize =
                    (Long) table.getItem(itemId).getItemProperty("file_size_bytes").getValue();

                entries.put(fileName,
                    new SimpleEntry<String, Long>(datasetCode, datasetFileSize));
            } else {
                entries.remove(fileName);
            }
        }
    }

    /**
     * The input should have the following form: type=openbis_type&id=openbis_id e.g. type=sample&id=/ABI_SYSBIO/QMARI117AV It is specifically designed to be
     * used in the case of datasetView. In other cases there is no guarantee that it will work correctly. returns a map with two entries: "type": "openbistype"
     * "id" : "openbisId"
     */
    public static Map<String, String> getMap(String parameters) {
        if (parameters == null || parameters.equals("")) {
            return null;
        }
        String[] params = parameters.split("&");
        // TODO check for length == 2 needed ?
        // if (params == null || params.length != 2)
        if (params == null || params.length > 3) {
            return null;
        }
        HashMap<String, String> map = new HashMap<String, String>();
        for (String p : params) {
            String[] kv = p.split("=");
            if (kv.length != 2) {
                return null;
            }
            map.put(kv[0], kv[1]);
        }
        return map;
    }

    // Recursively get all samples which are above the corresponding sample in the tree
    public Set<Sample> getAllChildren(Set<Sample> found, Sample sample) {
        // List<Sample> current = datahandler.getOpenBisClient().getChildrenSamples(sample);
        List<Sample> current = sample.getChildren();

        if (current.size() == 0) {
            return found;
        }

        for (int i = 0; i < current.size(); i++) {
            found.add(current.get(i));
            getAllChildren(found, current.get(i));
        }
        return found;
    }
}
