package smptk
package io

import image.Image._
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import image.Interpolation._
import image._

import java.io.File
import scala.util.Success
import scala.util.Failure


class ImageIOTest extends FunSpec with ShouldMatchers {
  
  describe("A 2D vector image") { 
    it ("can be stored and read again") {
    	val domain = DiscreteImageDomain2D((1f, 0f), (0.5f, 1f), (2, 3))
        val discreteImage = DiscreteScalarImage2D[Float](domain, IndexedSeq(1.4f, 2.1f, 7.5f, 9f, 8f, 0f))
        
         val tmpImgFile = File.createTempFile("image2D", ".h5")

        ImageIO.writeImage(discreteImage, tmpImgFile)
        val restoredDiscreteImgOrFailure = ImageIO.read2DScalarImage[Float](tmpImgFile)
        
        restoredDiscreteImgOrFailure.isSuccess should be (true)            	    	
        discreteImage should equal (restoredDiscreteImgOrFailure.get)
       
        tmpImgFile.delete()
    }
  }
  
}