package com.net2plan.gui.utils.topologyPane.jung.map;

import com.net2plan.gui.utils.INetworkCallback;
import com.net2plan.gui.utils.topologyPane.GUILink;
import com.net2plan.gui.utils.topologyPane.GUINode;
import com.net2plan.gui.utils.topologyPane.TopologyPanel;
import com.net2plan.gui.utils.topologyPane.jung.JUNGCanvas;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.internal.plugins.ITopologyCanvas;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Jorge San Emeterio on 03/11/2016.
 */
public class MapController
{
    private static final MapPanel mapViewer;

    private static final Thread renderThread;
    private static final LoadFrame loadFrame;

    private static TopologyPanel topologyPanel;
    private static ITopologyCanvas canvas;
    private static INetworkCallback callback;

    static
    {
        mapViewer = new MapPanel();
        loadFrame = new LoadFrame();
        renderThread = new Thread(loadFrame);
    }

    // Non-instanciable
    private MapController()
    {
    }

    public static void runMap(final TopologyPanel topologyPanel, final ITopologyCanvas canvas, final INetworkCallback callback)
    {
        MapController.topologyPanel = topologyPanel;
        MapController.canvas = canvas;
        MapController.callback = callback;

        // 1st step: Loading the map
        loadMap();

        // Waiting for the map to render
        synchronized (this)
        {
            try
            {
                renderThread.wait();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        // 2nd step: Loading the snapshot
        loadSnapshot();
    }

    private static void loadMap()
    {
        topologyPanel.zoomAll();

        // JUNG Canvas
        final VisualizationViewer<GUINode, GUILink> vv = (VisualizationViewer<GUINode, GUILink>) canvas.getComponent();

        // Getting viewport rectangle
        final Rectangle viewInLayoutUnits = vv.getRenderContext().getMultiLayerTransformer().inverseTransform(vv.getBounds()).getBounds();

        // Viewport center point.
        final Point2D centerPoint = new Point.Double(viewInLayoutUnits.getCenterX(), viewInLayoutUnits.getCenterY());
        final GeoPosition position = new GeoPosition(-centerPoint.getY(), centerPoint.getX());

        mapViewer.setCenterPosition(position);

        final Set<GeoPosition> positionSet = new HashSet<>();

        for (Node node : callback.getDesign().getNodes())
        {
            // Transforming N2P coords to geo position.
            final GeoPosition geoPosition = new GeoPosition(node.getXYPositionMap().getY(), node.getXYPositionMap().getX());
            positionSet.add(geoPosition);

            // The position that the node really takes on the map. This is the point where the map and the nodes align.
            final Point2D realPosition = mapViewer.getTileFactory().geoToPixel(geoPosition, mapViewer.getZoom());
            callback.moveNode(node.getId(), new Point2D.Double(realPosition.getX(), -realPosition.getY()));
        }

        mapViewer.zoomToBestFit(positionSet, 0.7);

        // Running the render thread
        renderThread.start();
    }

    private static void loadSnapshot()
    {
        topologyPanel.zoomAll();
        final int w = canvas.getComponent().getWidth();
        final int h = canvas.getComponent().getHeight();

        // Getting snapshot
        final File file = mapViewer.saveMap(w, h);

        // Aligning the snapshot with the previous map
        final VisualizationViewer<GUINode, GUILink> vv = (VisualizationViewer<GUINode, GUILink>) canvas.getComponent();
        final Rectangle viewInLayoutUnits = vv.getRenderContext().getMultiLayerTransformer().inverseTransform(vv.getBounds()).getBounds();

        final Point2D mapCorner = new Point.Double(viewInLayoutUnits.getCenterX() - (w / 2), viewInLayoutUnits.getCenterY() - (h / 2));
        final Double mapCornerX = mapCorner.getX();
        final Double mapCornerY = mapCorner.getY();

        // Setting the photo as background
        ((JUNGCanvas) canvas).setBackgroundImage(file, mapCornerX.intValue(), mapCornerY.intValue());
    }

    private static class LoadFrame implements Runnable
    {
        private final JFrame loadFrame;

        public LoadFrame()
        {
            loadFrame = new JFrame();
            loadFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            loadFrame.setLayout(new BorderLayout());
            loadFrame.setResizable(false);
            loadFrame.setTitle("Loading map...");

            loadFrame.addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowClosing(WindowEvent e)
                {
                    super.windowClosing(e);

                    try
                    {
                        renderThread.join();
                    } catch (InterruptedException ex)
                    {
                        ex.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void run()
        {
            loadFrame.setSize(new Dimension(topologyPanel.getWidth(), topologyPanel.getHeight()));
            loadFrame.getContentPane().add(mapViewer, BorderLayout.CENTER);
            loadFrame.setVisible(true);

            synchronized (this)
            {
                final int width = mapViewer.getWidth();
                final int height = mapViewer.getHeight();
                final int zoom = mapViewer.getZoom();

                final TileFactory tileFactory = mapViewer.getTileFactory();

                // Wait for load
                while (true)
                {
                    for (int i = 0; i < width; i++)
                    {
                        for (int j = 0; j < height; j++)
                        {
                            if (!tileFactory.getTile(i, j, zoom).isLoaded())
                            {
                                continue;
                            }
                        }
                    }

                    break;
                }

                this.notify();
            }
        }
    }
}
