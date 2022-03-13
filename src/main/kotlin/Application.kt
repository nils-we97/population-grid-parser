import org.osgeo.proj4j.CRSFactory
import org.osgeo.proj4j.CoordinateTransform
import org.osgeo.proj4j.CoordinateTransformFactory
import org.osgeo.proj4j.ProjCoordinate
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

open class Application
fun main(args: Array<String>) {
    if(args.size != 3) {
        throw RuntimeException("Additional Parameters: <GERMANY/EUROPE> <Input File> <Output Directory>")
    }

    val type = args[0]
    val inputFile = args[1]
    val outputDirectory = args[2]

    val resolutionGermany = when(type.uppercase()) {
        "GERMANY" -> true
        "EUROPE" -> false
        else -> throw RuntimeException("Invalid Population Grid. Expected GERMANY/EUROPE")
    }

    val records: LinkedList<CSVRecord> = LinkedList()
    val br = BufferedReader(FileReader(inputFile))
    var currentLine: String?

    while (br.readLine().also { currentLine = it } != null) {
        if(resolutionGermany) {
            if (currentLine!!.startsWith("Gitter_ID")) continue

            val values = currentLine!!.split(";")

            if (values.size == 4) {
                val centerX = values[1].toDoubleOrNull() ?: throw RuntimeException("Invalid X value ${values[1]}")
                val centerY = values[2].toDoubleOrNull() ?: throw RuntimeException("Invalid Y value! ${values[2]}")
                val population = values[3].toIntOrNull() ?: throw RuntimeException("Invalid Population Count! ${values[3]}")

                if (population > 0) {
                    records.add(CSVRecord(centerX, centerY, population))
                }
            } else {
                println("Failed to parse line")
            }
        } else {
            if (currentLine!!.startsWith("TOT_P")) continue

            val values = currentLine!!.split(",")

            if (values.size > 2) {
                val population = values[0].toIntOrNull() ?: throw RuntimeException("non int value found: ${values[0]}")
                val coordLine = values[1].replace("1kmN", "").split("E")

                if (coordLine.size == 2) {
                    val centerX = coordLine[1].toDoubleOrNull() ?: throw RuntimeException("failed to parse e coord: ${coordLine[1]}")
                    val centerY = coordLine[0].toDoubleOrNull() ?: throw RuntimeException("failed to parse n coord: ${coordLine[0]}")

                    if (population > 0) {
                        records.add(CSVRecord(centerX * 1000 + 500, centerY * 1000 + 500, population))
                    }
                } else {
                    println("Failed to parse line")
                }
            }
        }
    }

    val totalPopulation = records.map { it.population }.sum()
    println("Successfully parsed csv file, total population: $totalPopulation, number of non-empty records: ${records.size}")

    val ctFactory = CoordinateTransformFactory()
    val csFactory = CRSFactory()
    val crs1 = csFactory.createFromName("EPSG:3035")
    val crs2 = csFactory.createFromName("EPSG:4326")

    val trans: CoordinateTransform = ctFactory.createTransform(crs1, crs2)

    val transformedRecords = records
        .map { record ->
            val p1 = ProjCoordinate(record.x, record.y)
            val p2 = ProjCoordinate()

            trans.transform(p1, p2)

            CSVRecord(p2.x, p2.y, record.population)
        }.toList()

    println("Finished transformation")

    val file1 = File("$outputDirectory\\grid_x")
    val buffer1 = ByteBuffer.allocate(Double.SIZE_BYTES * transformedRecords.size).order(ByteOrder.LITTLE_ENDIAN)
    transformedRecords.forEach { buffer1.putDouble(it.x) }
    file1.writeBytes(buffer1.array())
    buffer1.clear()

    println("Finished x coords")

    val file2 = File("$outputDirectory\\grid_y")
    val buffer2 = ByteBuffer.allocate(Double.SIZE_BYTES * transformedRecords.size).order(ByteOrder.LITTLE_ENDIAN)
    transformedRecords.forEach { buffer2.putDouble(it.y) }
    file2.writeBytes(buffer2.array())
    buffer2.clear()

    println("Finished y coords")

    val file3 = File("$outputDirectory\\population")
    val buffer3 = ByteBuffer.allocate(Int.SIZE_BYTES * transformedRecords.size).order(ByteOrder.LITTLE_ENDIAN)
    transformedRecords.forEach { buffer3.putInt(it.population) }
    file3.writeBytes(buffer3.array())
    buffer3.clear()

    println("Finished population")

    val file4 = File("$outputDirectory\\result.csv")
    file4.writeText("x;y;population\n")
    transformedRecords.forEach { record -> file4.appendText("${record.x};${record.y};${record.population}\n") }

    println("finished writing files")
}

data class CSVRecord(
    val x: Double,
    val y: Double,
    val population: Int
)