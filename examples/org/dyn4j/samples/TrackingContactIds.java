/*
 * Copyright (c) 2010-2015 William Bittle  http://www.dyn4j.org/
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted 
 * provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice, this list of conditions 
 *     and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation and/or other materials provided with the 
 *     distribution.
 *   * Neither the name of dyn4j nor the names of its contributors may be used to endorse or 
 *     promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR 
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER 
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.dyn4j.samples;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.World;
import org.dyn4j.dynamics.contact.ContactListener;
import org.dyn4j.dynamics.contact.ContactPoint;
import org.dyn4j.dynamics.contact.ContactPointId;
import org.dyn4j.dynamics.contact.PersistedContactPoint;
import org.dyn4j.dynamics.contact.SolvedContactPoint;
import org.dyn4j.geometry.Circle;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Geometry;
import org.dyn4j.geometry.MassType;
import org.dyn4j.geometry.Polygon;
import org.dyn4j.geometry.Vector2;

/**
 * A simple scene with a few shape types that tracks the creation,
 * persistence and removal of contacts by using their unique ids.
 * @author William Bittle
 * @version 3.2.0
 * @since 3.0.0
 */
public class TrackingContactIds extends JFrame implements ContactListener {
	/** The serial version id */
	private static final long serialVersionUID = 5663760293144882635L;
	
	/** The scale 45 pixels per meter */
	public static final double SCALE = 45.0;
	
	/** The conversion factor from nano to base */
	public static final double NANO_TO_BASE = 1.0e9;
	
	/**
	 * Custom Body class to add drawing functionality.
	 * @author William Bittle
	 * @version 3.0.2
	 * @since 3.0.0
	 */
	public static class GameObject extends Body {
		/** The color of the object */
		protected Color color;
		
		/**
		 * Default constructor.
		 */
		public GameObject() {
			// randomly generate the color
			this.color = new Color(
					(float)Math.random() * 0.5f + 0.5f,
					(float)Math.random() * 0.5f + 0.5f,
					(float)Math.random() * 0.5f + 0.5f);
		}
		
		/**
		 * Draws the body.
		 * <p>
		 * Only coded for polygons and circles.
		 * @param g the graphics object to render to
		 */
		public void render(Graphics2D g) {
			// save the original transform
			AffineTransform ot = g.getTransform();
			
			// transform the coordinate system from world coordinates to local coordinates
			AffineTransform lt = new AffineTransform();
			lt.translate(this.transform.getTranslationX() * SCALE, this.transform.getTranslationY() * SCALE);
			lt.rotate(this.transform.getRotation());
			
			// apply the transform
			g.transform(lt);
			
			// loop over all the body fixtures for this body
			for (BodyFixture fixture : this.fixtures) {
				// get the shape on the fixture
				Convex convex = fixture.getShape();
				// check the shape type
				if (convex instanceof Polygon) {
					// since Triangle, Rectangle, and Polygon are all of
					// type Polygon in addition to their main type
					Polygon p = (Polygon) convex;
					int l = p.getVertices().length;
					int[] x = new int[l];
					int[] y = new int[l];
					
					int i = 0;
					for (Vector2 v : p.getVertices()) {
						x[i] = (int)(v.x * SCALE);
						y[i] = (int)(v.y * SCALE);
						i++;
					}
					
					java.awt.Polygon poly = new java.awt.Polygon(x, y, l);
					
					// set the color
					g.setColor(this.color);
					// fill the shape
					g.fillPolygon(poly);
					// set the color
					g.setColor(this.color.darker());
					// draw the shape
					g.drawPolygon(poly);
				} else if (convex instanceof Circle) {
					// cast the shape to get the radius
					Circle c = (Circle) convex;
					double r = c.getRadius();
					Vector2 cc = c.getCenter();
					int x = (int)Math.ceil((cc.x - r) * SCALE);
					int y = (int)Math.ceil((cc.y - r) * SCALE);
					int w = (int)Math.ceil(r * 2 * SCALE);
					// set the color
					g.setColor(this.color);
					// fill the shape
					g.fillOval(x, y, w, w);
					// set the color
					g.setColor(this.color.darker());
					// draw the shape
					g.drawOval(x, y, w, w);
				}
			}
			
			// set the original transform
			g.setTransform(ot);
		}
	}
	
	/** The canvas to draw to */
	protected Canvas canvas;
	
	/** The dynamics engine */
	protected World world;
	
	/** Wether the example is stopped or not */
	protected boolean stopped;
	
	/** The time stamp for the last iteration */
	protected long last;
	
	/**
	 * Default constructor for the window
	 */
	public TrackingContactIds() {
		super("Graphics2D Example");
		// setup the JFrame
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// add a window listener
		this.addWindowListener(new WindowAdapter() {
			/* (non-Javadoc)
			 * @see java.awt.event.WindowAdapter#windowClosing(java.awt.event.WindowEvent)
			 */
			@Override
			public void windowClosing(WindowEvent e) {
				// before we stop the JVM stop the example
				stop();
				super.windowClosing(e);
			}
		});
		
		// create the size of the window
		Dimension size = new Dimension(800, 600);
		
		// create a canvas to paint to 
		this.canvas = new Canvas();
		this.canvas.setPreferredSize(size);
		this.canvas.setMinimumSize(size);
		this.canvas.setMaximumSize(size);
		
		// add the canvas to the JFrame
		this.add(this.canvas);
		
		// make the JFrame not resizable
		// (this way I dont have to worry about resize events)
		this.setResizable(false);
		
		// size everything
		this.pack();
		
		// make sure we are not stopped
		this.stopped = false;
		
		// setup the world
		this.initializeWorld();
	}
	
	/**
	 * Creates game objects and adds them to the world.
	 * <p>
	 * Basically the same shapes from the Shapes test in
	 * the TestBed.
	 */
	protected void initializeWorld() {
		// create the world
		this.world = new World();
		
		// create all your bodies/joints
		
		// create the floor
		GameObject floor = new GameObject();
		floor.addFixture(Geometry.createRectangle(15, 1));
		floor.setMass(MassType.INFINITE);
		// move the floor down a bit
		floor.translate(0.0, -4.0);
		this.world.addBody(floor);
		
		// create a triangle object
		GameObject triangle = new GameObject();
		triangle.addFixture(Geometry.createTriangle(
				new Vector2(0.0, 0.5), 
				new Vector2(-0.5, -0.5), 
				new Vector2(0.5, -0.5)));
		triangle.setMass(MassType.NORMAL);
		triangle.translate(-1.0, 2.0);
		// test having a velocity
		triangle.getLinearVelocity().set(5.0, 0.0);
		this.world.addBody(triangle);
		
		// create a circle
		GameObject circle = new GameObject();
		circle.addFixture(Geometry.createCircle(0.5));
		circle.setMass(MassType.NORMAL);
		circle.translate(2.0, 2.0);
		// test adding some force
		circle.applyForce(new Vector2(-100.0, 0.0));
		// set some linear damping to simulate rolling friction
		circle.setLinearDamping(0.05);
		this.world.addBody(circle);
		
		// try a rectangle
		GameObject rectangle = new GameObject();
		rectangle.addFixture(Geometry.createRectangle(1, 1));
		rectangle.setMass(MassType.NORMAL);
		rectangle.translate(0.0, 2.0);
		rectangle.getLinearVelocity().set(-5.0, 0.0);
		this.world.addBody(rectangle);
		
		// try a polygon with lots of vertices
		GameObject polygon = new GameObject();
		polygon.addFixture(Geometry.createUnitCirclePolygon(10, 1));
		polygon.setMass(MassType.NORMAL);
		polygon.translate(-2.5, 2.0);
		// set the angular velocity
		polygon.setAngularVelocity(Math.toRadians(-20.0));
		this.world.addBody(polygon);
		
		// try a compound object (Capsule)
		BodyFixture c1Fixture = new BodyFixture(Geometry.createCircle(0.5));
		BodyFixture c2Fixture = new BodyFixture(Geometry.createCircle(0.5));
		c1Fixture.setDensity(0.5);
		c2Fixture.setDensity(0.5);
		// translate the circles in local coordinates
		c1Fixture.getShape().translate(-1.0, 0.0);
		c2Fixture.getShape().translate(1.0, 0.0);
		GameObject capsule = new GameObject();
		capsule.addFixture(c1Fixture);
		capsule.addFixture(c2Fixture);
		capsule.addFixture(Geometry.createRectangle(2, 1));
		capsule.setMass(MassType.NORMAL);
		capsule.translate(0.0, 4.0);
		this.world.addBody(capsule);
		
		GameObject issTri = new GameObject();
		issTri.addFixture(Geometry.createIsoscelesTriangle(1.0, 3.0));
		issTri.setMass(MassType.NORMAL);
		issTri.translate(2.0, 3.0);
		this.world.addBody(issTri);
		
		GameObject equTri = new GameObject();
		equTri.addFixture(Geometry.createEquilateralTriangle(2.0));
		equTri.setMass(MassType.NORMAL);
		equTri.translate(3.0, 3.0);
		this.world.addBody(equTri);
		
		GameObject rightTri = new GameObject();
		rightTri.addFixture(Geometry.createRightTriangle(2.0, 1.0));
		rightTri.setMass(MassType.NORMAL);
		rightTri.translate(4.0, 3.0);
		this.world.addBody(rightTri);
		
		// attach the contact listener
		this.world.addListener(this);
	}
	
	/**
	 * Start active rendering the example.
	 * <p>
	 * This should be called after the JFrame has been shown.
	 */
	public void start() {
		// initialize the last update time
		this.last = System.nanoTime();
		// don't allow AWT to paint the canvas since we are
		this.canvas.setIgnoreRepaint(true);
		// enable double buffering (the JFrame has to be
		// visible before this can be done)
		this.canvas.createBufferStrategy(2);
		// run a separate thread to do active rendering
		// because we don't want to do it on the EDT
		Thread thread = new Thread() {
			public void run() {
				// perform an infinite loop stopped
				// render as fast as possible
				while (!isStopped()) {
					gameLoop();
					// you could add a Thread.yield(); or
					// Thread.sleep(long) here to give the
					// CPU some breathing room
				}
			}
		};
		// set the game loop thread to a daemon thread so that
		// it cannot stop the JVM from exiting
		thread.setDaemon(true);
		// start the game loop
		thread.start();
	}
	
	/**
	 * The method calling the necessary methods to update
	 * the game, graphics, and poll for input.
	 */
	protected void gameLoop() {
		// get the graphics object to render to
		Graphics2D g = (Graphics2D)this.canvas.getBufferStrategy().getDrawGraphics();
		
		// before we render everything im going to flip the y axis and move the
		// origin to the center (instead of it being in the top left corner)
		AffineTransform yFlip = AffineTransform.getScaleInstance(1, -1);
		AffineTransform move = AffineTransform.getTranslateInstance(400, -300);
		g.transform(yFlip);
		g.transform(move);
		
		// now (0, 0) is in the center of the screen with the positive x axis
		// pointing right and the positive y axis pointing up
		
		// render anything about the Example (will render the World objects)
		this.render(g);
		
		// dispose of the graphics object
		g.dispose();
		
		// blit/flip the buffer
		BufferStrategy strategy = this.canvas.getBufferStrategy();
		if (!strategy.contentsLost()) {
			strategy.show();
		}
		
		// Sync the display on some systems.
        // (on Linux, this fixes event queue problems)
        Toolkit.getDefaultToolkit().sync();
        
        // update the World
        
        // get the current time
        long time = System.nanoTime();
        // get the elapsed time from the last iteration
        long diff = time - this.last;
        // set the last time
        this.last = time;
    	// convert from nanoseconds to seconds
    	double elapsedTime = (double)diff / NANO_TO_BASE;
        // update the world with the elapsed time
        this.world.update(elapsedTime);
	}

	/**
	 * Renders the example.
	 * @param g the graphics object to render to
	 */
	protected void render(Graphics2D g) {
		// lets draw over everything with a white background
		g.setColor(Color.WHITE);
		g.fillRect(-400, -300, 800, 600);
		
		// lets move the view up some
		g.translate(0.0, -1.0 * SCALE);
		
		// draw all the objects in the world
		for (int i = 0; i < this.world.getBodyCount(); i++) {
			// get the object
			GameObject go = (GameObject) this.world.getBody(i);
			// draw the object
			go.render(g);
		}
	}
	
	/**
	 * Stops the example.
	 */
	public synchronized void stop() {
		this.stopped = true;
	}
	
	/**
	 * Returns true if the example is stopped.
	 * @return boolean true if stopped
	 */
	public synchronized boolean isStopped() {
		return this.stopped;
	}
	
	/**
	 * Entry point for the example application.
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		// set the look and feel to the system look and feel
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		
		// create the example JFrame
		TrackingContactIds window = new TrackingContactIds();
		
		// show it
		window.setVisible(true);
		
		// start it
		window.start();
	}
	
	// contact listening
	
	private Map<ContactPointId, UUID> ids = new HashMap<ContactPointId, UUID>();
	
	@Override
	public boolean begin(ContactPoint point) {
		ContactPointId id = point.getId();
		UUID uuid = UUID.randomUUID();
		System.out.println("Begin:   " + uuid.toString());
		ids.put(id, uuid);
		return true;
	}
	
	@Override
	public void end(ContactPoint point) {
		ContactPointId id = point.getId();
		UUID uuid = ids.remove(id);
		if (uuid != null) {
			System.out.println("End:     " + uuid.toString());
		} else {
			System.err.println("Shouldn't happen");
		}
	}
	
	@Override
	public boolean persist(PersistedContactPoint point) {
		ContactPointId id = point.getId();
		UUID uuid = ids.get(id);
		if (uuid == null) {
			System.err.println("Shouldn't happen");
		}
		return true;
	}
	
	@Override
	public void postSolve(SolvedContactPoint point) { }
	
	@Override
	public boolean preSolve(ContactPoint point) { return true; }
	
	@Override
	public void sensed(ContactPoint point) { }
}
