package de.metro.robocode;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import robocode.AdvancedRobot;
import robocode.Condition;
import robocode.RobotDeathEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

public class AdeBot extends AdvancedRobot {
	  private static final double TWO_PI = Math.PI * 2;
	  
	  private static Rectangle2D.Double battleField;
	  private static Point2D.Double destination;
	  private static String nearestName;
	  private static double nearestDistance;
	  private static Map<String, EnemyData> enemies = new HashMap<String, EnemyData>();
	  private static List<Point2D.Double> recentLocations;
	 
	  public void run() {
	    setAdjustGunForRobotTurn(true);
	    setAdjustRadarForGunTurn(true);
	    setColors(Color.red, Color.red, new Color(141, 220, 175));
	 
	    battleField = new Rectangle2D.Double(50, 50, getBattleFieldWidth() - 100, getBattleFieldHeight() - 100);
	    recentLocations = new ArrayList<Point2D.Double>();
	    nearestDistance = Double.POSITIVE_INFINITY;
	    destination = null;
	 
	    do {
	      Point2D.Double myLocation = myLocation();
	      recentLocations.add(0, myLocation);
	 
	      // Gun
	      double bulletPower = 3 - ((20 - getEnergy()) / 6);
	      if (getGunTurnRemaining() == 0) {
	        setFire(bulletPower);
	      }
	 
	      List<MeleeFiringAngle> firingAngles = new ArrayList<MeleeFiringAngle>();
	      for (EnemyData enemyData : enemies.values()) {
	        if (enemyData.alive) {
	          double enemyDistance = enemyData.distance(myLocation);
	          int bulletTicks =
	              (int) (enemyDistance / Rules.getBulletSpeed(bulletPower));
	          for (Point2D.Double vector : enemyData.lastVectors) {
	            if (vector != null) {
	              Point2D.Double projectedLocation = project(enemyData,
	                  enemyData.heading + vector.x, vector.y * bulletTicks);
	              if (battleField.contains(projectedLocation)) {
	                firingAngles.add(new MeleeFiringAngle(
	                    absoluteBearing(myLocation, projectedLocation),
	                    enemyDistance, 18 / enemyDistance));
	              }
	            }
	          }
	        }
	      }
	 
	      try {
	        double bestDensity = 0;
	        for (int x = 0; x < 160; x++) {
	          double angle = Math.PI * x / 80;
	          double density = 0;
	          for (MeleeFiringAngle meleeAngle : firingAngles) {
	            double ux =
	                Math.abs(Utils.normalRelativeAngle(angle - meleeAngle.angle))
	                    / meleeAngle.bandwidth;
	            if (ux < 1) {
	              density += square(1 - square(ux)) / meleeAngle.distance;
	            }
	          }
	          if (density > bestDensity) {
	            bestDensity = density;
	            setTurnGunRightRadians(
	                Utils.normalRelativeAngle(angle - getGunHeadingRadians()));
	          }
	        }
	      } catch (NullPointerException npe) {
	      }
	      
	      // Movement
	      double bestRisk;
	      try {
	        bestRisk = evalDestinationRisk(destination) * .85;
	      } catch (NullPointerException ex) {
	        bestRisk = Double.POSITIVE_INFINITY;
	      }
	      try {
	        for (double d = 0; d < TWO_PI; d += 0.1) {
	          Point2D.Double newDest = project(myLocation, d,
	              Math.min(nearestDistance, 100 + Math.random() * 500));
	          double thisRisk = evalDestinationRisk(newDest);
	          if (battleField.contains(newDest) && thisRisk < bestRisk) {
	            bestRisk = thisRisk;
	            destination = newDest;
	          }
	        }
	 
	        double angle = Utils.normalRelativeAngle(
	            absoluteBearing(myLocation, destination) - getHeadingRadians());
	        setTurnRightRadians(Math.tan(angle));
	        setAhead(Math.cos(angle) * Double.POSITIVE_INFINITY);
	      } catch (NullPointerException ex) {
	      }
	 
	      // Radar
	      setTurnRadarRightRadians(1);
	      try {
	        long stalestTime = Long.MAX_VALUE;
	        for (EnemyData enemyData : enemies.values()) {
	          if (getTime() > 20 && enemyData.alive
	              && enemyData.lastScanTime < stalestTime) {
	            stalestTime = enemyData.lastScanTime;
	            setTurnRadarRightRadians(Math.signum(Utils.normalRelativeAngle(
	                absoluteBearing(myLocation, enemyData)
	                    - getRadarHeadingRadians())));
	          }
	        }
	      } catch (NullPointerException npe) {
	      }
	      
	      execute();
	    } while (true);    
	  }
	 
	  public void onScannedRobot(ScannedRobotEvent e) {
	    double distance = e.getDistance();
	    String botName = e.getName();
	 
	    if (!enemies.containsKey(botName)) {
	      enemies.put(botName, new EnemyData());
	    }
	 
	    DisplacementTimer timer;
	    addCustomEvent(timer = new DisplacementTimer());
	    EnemyData enemyData = timer.enemyData = enemies.get(botName);
	    enemyData.energy = e.getEnergy();
	    enemyData.alive = true;
	    enemyData.lastScanTime = getTime();
	 
	    timer.displacementVector = (enemyData.lastVectors = enemyData.gunVectors
	        [(int) (distance / 300)]
	        [(int) (Math.abs(e.getVelocity()) / 4)])
	            [enemyData.nextIndex++ % 200] = new Point2D.Double(0, 0);
	 
	    enemyData.setLocation(timer.targetLocation = project(
	        myLocation(), e.getBearingRadians() + getHeadingRadians(),
	        distance));
	 
	    timer.bulletTicks = (int) (distance / 11);
	    timer.targetHeading = enemyData.heading = e.getHeadingRadians()
	        + (e.getVelocity() < 0 ? Math.PI : 0);
	 
	    if (distance < nearestDistance || botName.equals(nearestName)) {
	      nearestDistance = distance;
	      nearestName = botName;
	    }
	  }
	 
	  public void onRobotDeath(RobotDeathEvent e) {
	    enemies.get(e.getName()).alive = false;
	    nearestDistance = Double.POSITIVE_INFINITY;
	  }
	 
	  private double evalDestinationRisk(Point2D.Double destination) {
	    double risk = 0;
	 
	    for (EnemyData enemy1 : enemies.values()) {
	      double distSq = enemy1.distanceSq(destination);
	      int closer = 0;
	      for (EnemyData enemy2 : enemies.values()) {
	        if (enemy1.distanceSq(enemy2) < distSq) {
	          closer++;
	        }
	      }
	 
	      java.awt.geom.Point2D.Double myLocation = myLocation();
	      risk += Math.max(0.5, Math.min(enemy1.energy / getEnergy(), 2))
	          * (1 + Math.abs(Math.cos(absoluteBearing(myLocation, destination)
	              - absoluteBearing(myLocation, enemy1))))
	          / closer
	          / distSq
	          / (200000 + destination.distanceSq(
	              getBattleFieldWidth() / 2, getBattleFieldHeight() / 2));
	    }
	 
	    for (int x = 1; x < 6; x++) {
	      try {
	        risk *= 1 + (500 / x
	            / recentLocations.get(x * 10).distanceSq(destination));
	      } catch (Exception ex) {
	        // ok
	      }
	    }
	 
	    return risk;
	  }
	 
	  public static double absoluteBearing(
	      Point2D.Double source, Point2D.Double target) {
	    return Math.atan2(target.x - source.x, target.y - source.y);
	  }
	 
	  public static Point2D.Double project(Point2D.Double sourceLocation, 
	      double angle, double length) {
	    return new Point2D.Double(
	        sourceLocation.x + Math.sin(angle) * length,
	        sourceLocation.y + Math.cos(angle) * length);
	  }
	 
	  public static double square(double x) {
	    return x * x;
	  }
	 
	  private Point2D.Double myLocation() {
	    return new Point2D.Double(getX(), getY());
	  }
	 
	  public class DisplacementTimer extends Condition {
	    EnemyData enemyData;
	    Point2D.Double targetLocation;
	    double targetHeading;
	    Point2D.Double displacementVector;
	    int bulletTicks;
	    int timer;
	 
	    public boolean test() {
	      if (++timer > bulletTicks && enemyData.alive) {
	        displacementVector.setLocation(
	            absoluteBearing(targetLocation, enemyData) - targetHeading,
	            targetLocation.distance(enemyData) / bulletTicks);
	        removeCustomEvent(this);
	      }
	      return false;
	    }
	  }
	 
	  @SuppressWarnings("serial")
	  public static class EnemyData extends Point2D.Double {
	    public double energy;
	    public boolean alive;
	    public Point2D.Double[][][] gunVectors = new Point2D.Double[5][5][200];
	    public Point2D.Double[] lastVectors;
	    public int nextIndex = 0;
	    public double heading;
	    public long lastScanTime;
	  }
	 
	  public static class MeleeFiringAngle {
	    public double angle;
	    public double distance;
	    public double bandwidth;
	 
	    public MeleeFiringAngle(double angle, double distance, double bandwidth) {
	      this.angle = angle;
	      this.distance = distance;
	      this.bandwidth = bandwidth;
	    }
	  }
}
