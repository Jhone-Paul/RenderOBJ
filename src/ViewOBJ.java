import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class ViewOBJ {
    public static void main(String[] args) {
        String filePath = "FinalBaseMesh.obj";
        if (args.length > 0) {
            filePath = args[0];
            System.out.println("Showing file: " + filePath);
        }

        JFrame frame = new JFrame("OBJ Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container pane = frame.getContentPane();
        pane.setLayout(new BorderLayout());

        Model3D model = new Model3D();
        try {
            model.loadOBJ(filePath);
            System.out.println("Model loaded with " + model.vertices.size() + " vertices and " +
                    model.faces.size() + " faces.");
        } catch (IOException e) {
            System.err.println("Error loading model: " + e.getMessage());
            e.printStackTrace();
        }
        JToggleButton wireframe = new JToggleButton("Wireframe");
        pane.add(wireframe, BorderLayout.NORTH);

        JSlider headingSlider = new JSlider(0, 360, 180);
        pane.add(headingSlider, BorderLayout.SOUTH);

        JSlider pitchSlider = new JSlider(SwingConstants.VERTICAL, 0, 360, 180);
        pane.add(pitchSlider, BorderLayout.EAST);

        RenderPanel renderPanel = new RenderPanel(model, headingSlider, pitchSlider);
        pane.add(renderPanel, BorderLayout.CENTER);

        wireframe.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                renderPanel.toggleRenderMode();
            }
        });
        headingSlider.addChangeListener(e -> renderPanel.repaint());
        pitchSlider.addChangeListener(e -> renderPanel.repaint());

        frame.setSize(800, 800);
        frame.setVisible(true);
    }
}

class RenderPanel extends JPanel {
    private Model3D model;
    private JSlider headingSlider;
    private JSlider pitchSlider;

    private int lastX, lastY;
    private double headingValue = 180;
    private double pitchValue = 180;
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isPanning = false;
    private double zoomFactor = 1.0;
    private boolean wireframeMode = false;

    public void toggleRenderMode() {
        wireframeMode = !wireframeMode;
        repaint();
    }

    public RenderPanel(Model3D model, JSlider headingSlider, JSlider pitchSlider) {
        this.model = model;
        this.headingSlider = headingSlider;
        this.pitchSlider = pitchSlider;


        addMouseWheelListener(e -> {
            if (e.getPreciseWheelRotation() < 0) {
                zoomFactor *= 1.1; // Zoom in
            } else {
                zoomFactor *= 0.9; // Zoom out
            }
            zoomFactor = Math.max(0.1, Math.min(5.0, zoomFactor)); // Limit zoom range
            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();

                // Use right mouse button for panning, left for rotation
                isPanning = SwingUtilities.isRightMouseButton(e);
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastX;
                int dy = e.getY() - lastY;

                if (isPanning) {
                    // Pan the model
                    xOffset += dx;
                    yOffset += dy;
                } else {
                    // Rotate the model (existing functionality)
                    headingValue = (headingValue + dx * 0.5) % 360;
                    pitchValue = Math.max(0, Math.min(360, pitchValue + dy * 0.5));

                    headingSlider.setValue((int)headingValue);
                    pitchSlider.setValue((int)pitchValue);
                }

                lastX = e.getX();
                lastY = e.getY();
                repaint();
            }
        });

        setBackground(Color.BLACK);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (model == null || model.vertices.isEmpty()) {
            g2.setColor(Color.RED);
            g2.drawString("No model loaded or model is empty", 20, 30);
            return;
        }

        g2.setColor(Color.GREEN);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double heading = Math.toRadians(headingSlider.getValue());
        double pitch = Math.toRadians(pitchSlider.getValue());

        double maxSize = model.getMaxSize();
        double scale = Math.min(getWidth(), getHeight()) / maxSize * 0.5 * zoomFactor;

        int centerX = getWidth() / 2 + (int)xOffset;
        int centerY = getHeight() / 2 + (int)yOffset;

        for (Face face : model.faces) {
            int[] xPoints = new int[face.vertexIndices.size()];
            int[] yPoints = new int[face.vertexIndices.size()];

            for (int i = 0; i < face.vertexIndices.size(); i++) {
                int idx = face.vertexIndices.get(i) - 1;
                Vertex v = model.vertices.get(idx);

                double x = v.x;
                double y = v.y;
                double z = v.z;

                double newX = x * Math.cos(heading) - z * Math.sin(heading);
                double newZ = x * Math.sin(heading) + z * Math.cos(heading);
                x = newX;
                z = newZ;

                double newY = y * Math.cos(pitch) - z * Math.sin(pitch);
                newZ = y * Math.sin(pitch) + z * Math.cos(pitch);
                y = newY;
                z = newZ;

                double zOffset = 5.0;
                double screenX = x * scale / (z + zOffset) + centerX;
                double screenY = -y * scale / (z + zOffset) + centerY;

                xPoints[i] = (int) screenX;
                yPoints[i] = (int) screenY;
            }

            if (wireframeMode) {
                g2.drawPolygon(xPoints, yPoints, face.vertexIndices.size());
            } else {
                g2.fillPolygon(xPoints, yPoints, face.vertexIndices.size());
            }
        }
    }
}

class Model3D {
    List<Vertex> vertices = new ArrayList<>();
    List<Face> faces = new ArrayList<>();

    public void loadOBJ(String filename) throws IOException {
        vertices.clear();
        faces.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("v ")) {
                    // Parse vertex
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 4) {
                        float x = Float.parseFloat(parts[1]);
                        float y = Float.parseFloat(parts[2]);
                        float z = Float.parseFloat(parts[3]);
                        vertices.add(new Vertex(x, y, z));
                    }
                } else if (line.startsWith("f ")) {
                    // Parse face
                    String[] parts = line.split("\\s+");
                    Face face = new Face();

                    // Start from 1 to skip the "f" prefix
                    for (int i = 1; i < parts.length; i++) {
                        String[] indices = parts[i].split("/");
                        int vertexIndex = Integer.parseInt(indices[0]);
                        face.vertexIndices.add(vertexIndex);
                    }

                    faces.add(face);
                }
                // Ignore other lines (like vt, vn, comments, etc.)
            }
        } catch (FileNotFoundException e) {
            System.err.println("Could not find file: " + filename);
            throw e;
        }
    }

    public double getMaxSize() {
        if (vertices.isEmpty()) {
            return 1.0;
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;

        for (Vertex v : vertices) {
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);

            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }

        double width = maxX - minX;
        double height = maxY - minY;
        double depth = maxZ - minZ;

        return Math.max(Math.max(width, height), depth);
    }
}

class Vertex {
    double x, y, z;

    public Vertex(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}

class Face {
    List<Integer> vertexIndices = new ArrayList<>();
}