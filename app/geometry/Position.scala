package geometry

case class GpsPos( lat: BigDecimal, lng: BigDecimal ) {
  def x = lat
  def y = lng
}
