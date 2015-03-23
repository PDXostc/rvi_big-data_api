package geometry

object Polygon {
  /*
   * isLeft(): Tests if a point is left, on or right of an
   *           infinite line.
   *
   * Input:
   *       p0: The point of origin of a polygon line.
   *       p1: The point of termination of a polygon line.
   *       p2: The point whose location is to be determined.
   *
   * Return:
   *       > 0 when p2 lies left of the line between p0 and p1.
   *       = 0 when p2 lies on the line.
   *       < 0 when p2 lies right of the line.
   */
  def isLeft(p0: GpsPos, p1: GpsPos, p2: GpsPos): BigDecimal = {
    (p1.x - p0.x) * (p2.y - p0.y) + (p2.x - p0.x) * (p1.y - p0.y)
  }


  /*
   * calcCrossingNumber(): Performs the crossing number test for a point
   *                       and a polygon.
   *
   * Input:
   *       p: The point to examine in the test.
   *       polygon: The polygon used in the test.
   *
   * Return:
   *       0: Point p is outside of the polygon.
   *       1: Point p is inside the polygon.
   */
  def calcCrossingNumber(p: GpsPos, polygon: Vector[GpsPos]): Int = {
    var cn: Int = 0 // number of ray - polygon cross sections
    var vt: BigDecimal = 0.0

    for(i <- 0 to (polygon.length - 1)) {
      if(((polygon(i).y <= p.y) && (polygon((i+1) % polygon.length).y > p.y))
        || ((polygon(i).y > p.y) && (polygon((i+1) % polygon.length).y <= p.y))) {
        vt = (p.y - polygon(i).y) / (polygon((i+1) % polygon.length).y - polygon(i).y)

        if(p.x < polygon(i).x + vt * (polygon((i+1) % polygon.length).x - polygon(i).x))
          cn = cn + 1
      }
    }
    (cn & 1)
  }

  /*
   * calcWindingNumber(): Calculates how many times the polygon winds around a point.
   *
   * Input:
   *       p: The point to examine in the test.
   *       polygon: The polygon used in the test.
   *
   * Return:
   *       0: Point p is outside of the polygon.
   *       Other than 0: Point p is inside the polygon.
   */
  def calcWindingNumber(p: GpsPos, polygon: Vector[GpsPos]): Int = {
    var wn: Int = 0

    for(i <- 0 to (polygon.length - 1)) {
      if(polygon(i).y <= p.y) {
        if(polygon((i+1) % polygon.length).y > p.y)
          if(isLeft(polygon(i), polygon((i+1) % polygon.length), p) > 0)
            wn = wn + 1
      }
      else {
        if(polygon((i+1) % polygon.length).y <= p.y)
          if(isLeft(polygon(i), polygon((i+1) % polygon.length), p) < 0)
            wn = wn - 1
      }
    }
    wn
  }

  /*
   * isPointInPolygon(): Verifies whether a point is inside a polygon.
   *                     It uses the result both from crossing number
   *                     and the winding number method in order to include
   *                     points that lie on one of the polygon's vertices.
   *
   * Input:
   *       p: The point to examine in the test.
   *       polygon: The polygon used in the test.
   *
   * Return:
   *       false: Point p is outside of the polygon.
   *       true: Point p is inside the polygon.
   */
  def isPointInPolygon(p: GpsPos, polygon: Vector[GpsPos]): Boolean = {
    (calcCrossingNumber(p,polygon) == 1) | (calcWindingNumber(p,polygon) != 0)
  }

  def main(args: Array[String]) {
    val pa0 = new GpsPos(5,5)
    val pb0 = new GpsPos(10,5)
    val pc0 = new GpsPos(15,5)

    val p1 = new GpsPos(10,10)
    val p2 = new GpsPos(10,2)
    val p3 = new GpsPos(20,5)
    val poly = Vector(p1,p2,p3)

    println("\nisPointInPolygon pa0:", isPointInPolygon(pa0,poly))

    println("\nisPointInPolygon pa0:", isPointInPolygon(pb0,poly))

    println("\nisPointInPolygon pa0:", isPointInPolygon(pc0,poly))
  }
}