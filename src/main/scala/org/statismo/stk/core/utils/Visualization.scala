package org.statismo.stk.core
package utils

import image.ScalarPixel
import breeze.plot._
import breeze.linalg._
import vtk._
import scala.swing.SimpleSwingApplication
import scala.swing.Component
import javax.swing.JPanel
import java.awt.BorderLayout
import scala.swing.MainFrame
import org.statismo.stk.core.image.DiscreteScalarImage1D
import org.statismo.stk.core.mesh.TriangleMesh
import org.statismo.stk.core.image.ContinuousScalarImage1D
import org.statismo.stk.core.image.DiscreteImageDomain1D
import org.statismo.stk.core.image.ContinuousVectorImage
import org.statismo.stk.core.image.DiscreteScalarImage2D
import org.statismo.stk.core.image.ContinuousScalarImage2D
import org.statismo.stk.core.image.DiscreteImageDomain2D
import org.statismo.stk.core.image.Resample
import org.statismo.stk.core.image._
import org.statismo.stk.core.common.BoxedDomain
import geometry._
import scala.util.Try
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import org.statismo.stk.core.common.{ BoxedDomain, BoxedDomain1D, BoxedDomain2D, BoxedDomain3D }
import org.statismo.stk.core.statisticalmodel.StatisticalMeshModel
import scala.swing.Slider
import scala.swing.BoxPanel
import scala.swing.Orientation
import org.statismo.stk.core.io.StatismoIO
import scala.swing.event.ValueChanged
import java.awt.geom.Dimension2D
import java.awt.Dimension
import scala.swing.Button
import scala.swing.event.{ ButtonClicked }
import org.statismo.stk.core.io.MeshIO
import javax.swing.UIManager
import javax.swing.WindowConstants.{ DISPOSE_ON_CLOSE, DO_NOTHING_ON_CLOSE }
import scala.collection.mutable.ListBuffer
import java.awt.Color
import scala.util.Success
import scala.util.Failure
import org.statismo.stk.core.io.ImageIO
import javax.swing.JLabel
import scala.swing.Label
import scala.swing.BorderPanel
import javax.swing.border.EmptyBorder
import javax.swing.border.BevelBorder
object Visualization {

  class VTKViewer() extends SimpleSwingApplication {

    def onEDT(prog: => Unit): Unit =
      if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
        javax.swing.SwingUtilities.invokeLater {
          new Runnable() {
            def run() = prog
          }
        }
      } else prog //val actors = ListBuffer[vtkActor]()

    // Setup VTK rendering panel, this also loads VTK native libraries
    var renWin: vtkPanel = new vtkCanvas

    // Create wrapper to integrate vtkPanel with Scala's Swing API
    val viewerPanel = new Component {
      override lazy val peer = new JPanel(new BorderLayout())
      peer.add(renWin)
    }

    val controlPanel = new BoxPanel(Orientation.Vertical) {
      def addControl(c: Component) = {
        contents += c
      }
    }

    // Create the main application window
    override def top = new MainFrame {
      title = "ScaleCone"
      contents = new BoxPanel(Orientation.Horizontal) {
        contents += controlPanel
        contents += viewerPanel
      }

      peer.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
      peer.setSize(800, 600)
      override def closeOperation() { dispose() }
    }

    def addMesh(mesh: TriangleMesh, color: Char = 'w') = {
      onEDT {
        val pd = MeshConversion.meshToVTKPolyData(mesh)
        var mapper = new vtkPolyDataMapper
        mapper.SetInputData(pd)
        mapper.ScalarVisibilityOff()

        var actor = new vtkActor
        actor.SetMapper(mapper)
        val colorAWT = colorCodeToAWTColor(color).get
        actor.GetProperty().SetColor(colorAWT.getRed() / 255, colorAWT.getGreen() / 255, colorAWT.getBlue() / 255)
        renWin.GetRenderer.AddActor(actor)
        resetSceneAndRender()

      }
    }

    def addImage[Pixel: ScalarPixel: ClassTag: TypeTag](img: DiscreteScalarImage3D[Pixel]) = {
      onEDT {

        val sp = ImageConversion.image3DTovtkStructuredPoints(img)
        val planeWidgetX = new vtkImagePlaneWidget()
        planeWidgetX.SetInputData(sp)
        planeWidgetX.SetPlaneOrientationToXAxes()
        planeWidgetX.SetInteractor(renWin.GetRenderWindow().GetInteractor())

        val planeWidgetY = new vtkImagePlaneWidget()
        planeWidgetY.SetInputData(sp)
        planeWidgetY.SetPlaneOrientationToYAxes()
        planeWidgetY.SetInteractor(renWin.GetRenderWindow().GetInteractor())

        val planeWidgetZ = new vtkImagePlaneWidget()
        planeWidgetZ.SetInputData(sp)
        planeWidgetZ.SetPlaneOrientationToZAxes()
        planeWidgetZ.SetInteractor(renWin.GetRenderWindow().GetInteractor())

        resetSceneAndRender()
        planeWidgetX.On()
        planeWidgetY.On()
        planeWidgetZ.On()

        val sliderX = new Slider { override val name = "x" }
        sliderX.min = 0; sliderX.max = sp.GetDimensions()(0); sliderX.majorTickSpacing = 1; sliderX.snapToTicks = true
        val sliderY = new Slider { override val name = "y" }
        sliderY.min = 0; sliderY.max = sp.GetDimensions()(1); sliderY.majorTickSpacing = 1; sliderY.snapToTicks = true
        val sliderZ = new Slider { override val name = "z" }
        sliderZ.min = 0; sliderZ.max = sp.GetDimensions()(2); sliderZ.majorTickSpacing = 1; sliderZ.snapToTicks = true
        listenTo(sliderX)
        listenTo(sliderY)
        listenTo(sliderZ)

        reactions += {
          case ValueChanged(s) => {
            s match {
              case slider: Slider if slider == sliderX => {
                planeWidgetX.SetSliceIndex(slider.value)
              }
              case slider: Slider if slider == sliderY => {
                planeWidgetY.SetSliceIndex(slider.value)
              }
              case slider: Slider if slider == sliderZ => {
                planeWidgetZ.SetSliceIndex(slider.value)
              }
              case _ => {}
            }
            renWin.Render
          }
        }

        val control = new BoxPanel(Orientation.Vertical) {
          border = new BevelBorder(BevelBorder.LOWERED)
          contents += new Label("Image Controls")
          contents += new BoxPanel(Orientation.Vertical) {
            contents += sliderX
            contents += sliderY
            contents += sliderZ
          }
        }
        controlPanel.addControl(control)

      }
    }

    private def colorCodeToAWTColor(c: Char): Try[Color] = {
      c match {
        case 'w' => Success(Color.WHITE)
        case 'k' => Success(Color.BLACK)
        case 'r' => Success(Color.RED)
        case 'g' => Success(Color.GREEN)
        case 'b' => Success(Color.BLUE)
        case _ => Failure(new Exception("Invalid color code " + c))
      }
    }

    def addPoints(pts: Seq[Point[ThreeD]], color: Char = 'w', size: Double = 2.0) = {
      onEDT {
        val sphereSrc = new vtkSphereSource()
        sphereSrc.SetRadius(size)
        val glyph = new vtkGlyph3D
        val ptsVTK = new vtkPoints()
        for (pt <- pts) {
          ptsVTK.InsertNextPoint(pt.data.map(_.toDouble))
        }
        val cells = new vtkCellArray()
        cells.SetNumberOfCells(0)

        val pd = new vtkPolyData()
        pd.SetPoints(ptsVTK)
        pd.SetPolys(cells)
        glyph.SetInputData(pd)
        glyph.SetSourceConnection(sphereSrc.GetOutputPort())
        var mapper = new vtkPolyDataMapper
        mapper.SetInputConnection(glyph.GetOutputPort())
        val actor = new vtkActor
        actor.SetMapper(mapper)
        val colorAWT = colorCodeToAWTColor(color).get
        actor.GetProperty().SetColor(colorAWT.getRed() / 255, colorAWT.getGreen() / 255, colorAWT.getBlue() / 255)
        renWin.GetRenderer.AddActor(actor)
        resetSceneAndRender()
      }
    }

    def addStatisticalModel(statmodel: StatisticalMeshModel, color: Char = 'w', numberOfBars: Int = 40) = {

      // TODO do cleanup

      val numPoints = statmodel.mesh.numberOfPoints
      val gp = statmodel.gp.specializeForPoints(statmodel.mesh.points.toIndexedSeq)
      val barchartActor = new vtkBarChartActor
      val bccoeffArray = new vtkFloatArray
      val coeffsVector = DenseVector.zeros[Float](statmodel.gp.rank)
      val bcDataObject = new vtkDataObject();
      val pd = MeshConversion.meshToVTKPolyData(statmodel.mesh)
      val ng = new vtkPolyDataNormals
      var mapper = new vtkPolyDataMapper
      var actor = new vtkActor

      onEDT {
        bccoeffArray.SetNumberOfTuples(numberOfBars + 1) // we add a "ghost bar"
        bccoeffArray.SetNumberOfComponents(1)
        bcDataObject.GetFieldData().AddArray(bccoeffArray);

        barchartActor.SetInput(bcDataObject)
        barchartActor.SetLegendVisibility(0)
        barchartActor.SetPosition(0, 0)
        barchartActor.SetWidth(1)
        barchartActor.SetHeight(0.2)
        barchartActor.SetDisplayPosition(100, 0)
        barchartActor.SetLabelVisibility(0)

        ng.SetInputData(pd)
        ng.Update()

        mapper.SetInputConnection(ng.GetOutputPort())

        actor.SetMapper(mapper)
        renWin.GetRenderer.AddActor2D(barchartActor)
        renWin.GetRenderer.AddActor(actor)

        updateCoeffs(coeffsVector) // render the mean
        renWin.GetRenderer.ResetCamera
      }
      def updateCoeffs(coeffs: DenseVector[Float]) {
        onEDT {
          val ptdefs = gp.instanceAtPoints(coeffs)
          val newptseq = for ((pt, df) <- ptdefs) yield (Array((pt(0) + df(0)).toFloat, (pt(1) + df(1)).toFloat, (pt(2) + df(2)).toFloat))
          val ptarray = newptseq.flatten
          val arrayVTK = new vtkFloatArray()
          arrayVTK.SetNumberOfValues(numPoints)
          arrayVTK.SetNumberOfComponents(3)
          arrayVTK.SetJavaArray(ptarray.toArray)

          pd.GetPoints().SetData(arrayVTK)
          pd.Modified()

          val array = coeffs(0 until numberOfBars + 1).toArray
          array(numberOfBars) = 8
          bccoeffArray.SetJavaArray(array)
          bccoeffArray.Modified()
          bcDataObject.Modified()

          // update bc array
          val colorAWT = colorCodeToAWTColor(color).get
          for (i <- 0 until numberOfBars) {
            if (coeffs(i) < 0)
              barchartActor.SetBarColor(i, 0, 1, 0)
            else
              barchartActor.SetBarColor(i, 1, 0, 0)
          }
          barchartActor.SetBarColor(numberOfBars, 0, 0, 0) // the last one is only to set the scale. it should not be shown

          barchartActor.Modified()
          renWin.Render()
        }
      }

      resetSceneAndRender()

      updateCoeffs _

    }

    def resetSceneAndRender() {
      onEDT {
        renWin.GetRenderer.ResetCamera
        renWin.Render
      }
    }

  }

  object VTKViewer {
    def apply() = {
      val app = new VTKViewer()
      app.main(Array(""))
      app
    }
  }

  private case class VTKImageViewer2D(sp: vtkStructuredPoints) extends SimpleSwingApplication {

    val vPanel = new vtkCanvas() {
      val style = new vtkInteractorStyleImage();
      style.SetInteractionModeToImage2D()
      val im = new vtkImageResliceMapper()
      val actor = new vtkImageActor()
      actor.SetInputData(sp)
      iren.SetInteractorStyle(style)
      ren.AddActor(actor)
      ren.SetRenderWindow(rw);
      iren.SetInteractorStyle(style);
      ren.ResetCamera()

    }

    val scalaPanel = new Component {
      override lazy val peer = new JPanel(new BorderLayout())
      peer.add(vPanel)
    }

    // Create the main application window
    override def top = new MainFrame {
      title = "ScaleImageViewer"
      contents = scalaPanel
      peer.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
      override def closeOperation() { dispose() }
    }
  }

  private case class VTKImageViewer3D(sp: vtkStructuredPoints) extends SimpleSwingApplication {

    val vPanel = new vtkCanvas() {
      val style = new vtkInteractorStyleImage();
      style.SetInteractionModeToImage3D()
      val im = new vtkImageResliceMapper()
      im.SetInputData(sp)
      im.SliceFacesCameraOn()
      im.SliceAtFocalPointOn()
      im.BorderOff()

      var actor = new vtkImageSlice
      actor.SetMapper(im)

      ren.AddActor(actor)
      ren.SetRenderWindow(rw);
      iren.SetInteractorStyle(style);
      ren.ResetCamera()

    }

    val scalaPanel = new Component {
      override lazy val peer = new JPanel(new BorderLayout())
      peer.add(vPanel)
    }

    // Create the main application window
    override def top = new MainFrame {
      title = "ScaleImageViewer"
      contents = scalaPanel
      peer.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
      peer.setSize(800, 600)
      override def closeOperation() { dispose() }

    }
  }

  private case class VTKStatmodelViewer(statmodel: StatisticalMeshModel) extends SimpleSwingApplication {
    val mesh = statmodel.mesh
    val pd = MeshConversion.meshToVTKPolyData(mesh)
    val gp = statmodel.gp.specializeForPoints(mesh.points.force)
    val numComps = gp.eigenPairs.size
    val coeffs = DenseVector.zeros[Float](numComps)

    // Setup VTK rendering panel, this also loads VTK native libraries
    var renWin: vtkPanel = new vtkPanel
    val sliders = for (i <- 0 until 30) yield {
      val slider = new Slider()
      slider.min = -5
      slider.max = 5
      slider.snapToTicks = true
      slider.majorTickSpacing = 1
      slider.name = i.toString
      slider.value = 0
      slider
    }
    val resetButton = new Button()
    resetButton.text = "reset"
    resetButton.name = "reset"

    val randomButton = new Button()
    randomButton.text = "random"
    randomButton.name = "random"
    val sliderPanel = new BoxPanel(Orientation.Vertical) {
      for (slider <- sliders) {
        contents += slider
        contents += resetButton
        contents += randomButton
      }

    }
    // Create wrapper to integrate vtkPanel with Scala's Swing API
    val scalaPanel = new Component {
      override lazy val peer = new JPanel(new BorderLayout())
      peer.add(renWin)
    }

    var mapper = new vtkPolyDataMapper
    mapper.SetInputData(pd)

    var actor = new vtkActor
    actor.SetMapper(mapper)

    updateMesh(coeffs) // render the mean
    renWin.GetRenderer.AddActor(actor)
    renWin.GetRenderer.ResetCamera

    def updateMesh(coeffs: DenseVector[Float]) {
      val ptdefs = gp.instanceAtPoints(coeffs)
      val newptseq = for ((pt, df) <- ptdefs) yield (Array((pt(0) + df(0)).toFloat, (pt(1) + df(1)).toFloat, (pt(2) + df(2)).toFloat))
      val ptarray = newptseq.flatten
      val arrayVTK = new vtkFloatArray()
      arrayVTK.SetNumberOfValues(mesh.numberOfPoints)
      arrayVTK.SetNumberOfComponents(3)
      arrayVTK.SetJavaArray(ptarray.toArray)

      pd.GetPoints().SetData(arrayVTK)
      pd.Modified()
      renWin.Render()

    }

    // Create the main application window
    override def top = new MainFrame {
      title = "ScaleCone"

      peer.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)
      override def closeOperation() { dispose() }

      contents = new BoxPanel(Orientation.Horizontal) {
        contents += scalaPanel
        contents += sliderPanel
      }
      size = new Dimension(1024, 700)
      for (slider <- sliders) {
        listenTo(slider)
      }
      listenTo(resetButton)
      listenTo(randomButton)
      reactions += {
        case ValueChanged(s) => {
          val slider = s.asInstanceOf[Slider]
          coeffs(slider.name.toInt) = slider.value.toFloat
          updateMesh(coeffs)
        }
        case ButtonClicked(b) => {
          b.name match {
            case "reset" => {
              for (slider <- sliders) slider.value = 0
              coeffs.foreach(s => 0)
              updateMesh(coeffs)
            }
            case "random" => {
              val gaussian = breeze.stats.distributions.Gaussian(0, 1)
              coeffs.foreach(s => 0)
              for ((slider, i) <- sliders.zipWithIndex) {
                val r = gaussian.draw.toFloat
                slider.value = 0
                coeffs(i) = r
              }
              updateMesh(coeffs)
            }
          }

        }

      }

    }
  }

  def show[D <: Dim, Pixel: ScalarPixel](img: DiscreteScalarImage1D[Pixel]): Unit = {
    val pixelConv = implicitly[ScalarPixel[Pixel]]
    val xs = img.domain.points.map(_(0).toDouble)

    val f = Figure()
    val p = f.subplot(0)

    p += plot(xs, img.pixelValues.map(pixelConv.toDouble(_)), '+')

  }

  def show[Pixel: ScalarPixel: ClassTag: TypeTag](img: DiscreteScalarImage2D[Pixel]) {
    val imgVTK = ImageConversion.image2DTovtkStructuredPoints(img)
    VTKImageViewer2D(imgVTK).main(Array(""))
    imgVTK.Delete()
  }

  def show[Pixel: ScalarPixel: ClassTag: TypeTag](img: DiscreteScalarImage3D[Pixel]) {
    val imgVTK = ImageConversion.image3DTovtkStructuredPoints(img)
    VTKImageViewer3D(imgVTK).main(Array(""))
    imgVTK.Delete()

  }

  def show(img: ContinuousScalarImage1D, domain: BoxedDomain1D, outsideValue: Double): Unit = {

    val xs = linspace(domain.origin(0), domain.extent(0), 512)
    val f = Figure()
    val p = f.subplot(0)

    val liftedImg = (x: Double) => img.liftPixelValue(Point1D(x.toFloat)).getOrElse(outsideValue.toFloat).toDouble
    p += plot(xs, xs.map(x => liftedImg(x)))
    f.refresh
    f.visible = true
  }

  def show(img: ContinuousScalarImage2D, domain: BoxedDomain2D, outsideValue: Double): Unit = {
    val spacing = (domain.extent - domain.origin) * (1.0 / 512.0).toFloat
    val discreteDomain = DiscreteImageDomain2D(domain.origin, spacing, Index2D(512, 512))
    val discreteImg = Resample.sample[Double](img, discreteDomain, outsideValue)
    show(discreteImg)
  }

  def show(img: ContinuousScalarImage3D, domain: BoxedDomain3D, outsideValue: Double): Unit = {
    val spacing = (domain.extent - domain.origin) * (1.0 / 256.0).toFloat
    val discreteDomain = DiscreteImageDomain3D(domain.origin, spacing, Index3D(256, 256, 256))
    val discreteImg = Resample.sample[Double](img, discreteDomain, outsideValue)
    show(discreteImg)
  }

  def show(model: StatisticalMeshModel) {
    VTKStatmodelViewer(model).main(Array(""))
  }

  // testing purpose
  def main(args: Array[String]): Unit = {
    org.statismo.stk.core.initialize()
    val model = StatismoIO.readStatismoMeshModel(new java.io.File("/tmp/facemodel.h5")).get
    //val mesh = MeshIO.readMesh(new java.io.File("/tmp/facemesh.h5")).get
    //    val img = ImageIO.read3DScalarImage[Short](new java.io.File("/tmp/img.h5")).get

    val viewer = VTKViewer()
    ////    viewer.addImage(img)
    val updateFun = viewer.addStatisticalModel(model, color = 'w')

    val pts = IndexedSeq(model.mesh.points(10000), model.mesh.points(20000))
    viewer.addPoints(pts, color = 'b', size = 2.0)

    for (i <- 0 until 20) {
      Thread.sleep(1000)
      val coeffs = for (_ <- 0 until model.gp.rank) yield breeze.stats.distributions.Gaussian(0, 1).draw().toFloat
      updateFun(DenseVector(coeffs.toArray))
    }
    //    viewer.addMesh(mesh.warp(p => p + Vector3D(150, 0, 0)), color = 'r')
    //    val img2 = ImageIO.read3DScalarImage[Short](new java.io.File("/tmp/img2.vtk")).get
    //    viewer.addImage(img2)
  }
}
