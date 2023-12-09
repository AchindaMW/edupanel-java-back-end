package lk.ijse.dep11.edupanel.api;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import lk.ijse.dep11.edupanel.to.request.LecturerRequestTO;
import lk.ijse.dep11.edupanel.to.response.LecturerResTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import javax.validation.Valid;
import java.sql.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/lecturers")
@CrossOrigin
public class LecturerHttpController {

    @Autowired
    private DataSource pool;

    @Autowired
    private Bucket bucket;


    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = "multipart/form-data",produces = "application/json")
    public LecturerResTO createNewLecturer(@ModelAttribute @Valid LecturerRequestTO lecturer){
        try (Connection connection = pool.getConnection()) {

            /*Turn on the transaction for the connection*/
            connection.setAutoCommit(false);
            try {
                /*Add the lecturer to the main table*/
                PreparedStatement stmInsertLecturer = connection.prepareStatement("INSERT INTO lecturer (name, designation, qualifications, linkedin) " +
                        "VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                stmInsertLecturer.setString(1,lecturer.getName());
                stmInsertLecturer.setString(2,lecturer.getDesignation());
                stmInsertLecturer.setString(3,lecturer.getQualifications());
                stmInsertLecturer.setString(4,lecturer.getLinkedin());
                stmInsertLecturer.executeUpdate();
                ResultSet generatedKeys = stmInsertLecturer.getGeneratedKeys();
                generatedKeys.next();
                int lecturerId = generatedKeys.getInt(1);
                String picture = lecturerId + "-" + lecturer.getName();

                /*Update the main table inserting the picture if provided*/
                if (lecturer.getPicture() != null && !lecturer.getPicture().isEmpty()){
                    PreparedStatement stmUpdateLecturer = connection.prepareStatement("UPDATE lecturer SET picture = ? WHERE id = ?");
                    stmUpdateLecturer.setString(1,picture);
                    stmUpdateLecturer.setInt(2,lecturerId);
                    stmUpdateLecturer.executeUpdate();
                }

                /*Insert lecturer to the appropriate type table*/
                final String table = lecturer.getType().equalsIgnoreCase("full-time") ? "full_time_rank" : "part_time_rank";
                Statement stm = connection.createStatement();
                ResultSet rst = stm.executeQuery("SELECT `rank` FROM " + table + " ORDER BY `rank` DESC LIMIT 1");
                int rank;
                if (!rst.next()) rank = 1;
                else rank = rst.getInt("rank")+1;

                PreparedStatement stmInsertRank = connection.prepareStatement("INSERT INTO " + table + " (lecturer_id, `rank`) VALUES (?,?)");
                stmInsertRank.setInt(1,lecturerId);
                stmInsertRank.setInt(2,rank);
                stmInsertRank.executeUpdate();

                /*Insert lecturer's picture to the firebase database*/
                String pictureUrl = null;
                if (lecturer.getPicture() != null && !lecturer.getPicture().isEmpty()) {
                    Blob blob = bucket.create(picture, lecturer.getPicture().getInputStream(),
                            lecturer.getPicture().getContentType());
                    pictureUrl = blob
                            .signUrl(1, TimeUnit.DAYS, Storage.SignUrlOption.withV4Signature())
                            .toString();
                }
                connection.commit();

                /*Return the details of added lecturer*/
                return new LecturerResTO(lecturerId,
                        lecturer.getName(),
                        lecturer.getDesignation(),
                        lecturer.getQualifications(),
                        lecturer.getType(),
                        pictureUrl,
                        lecturer.getLinkedin());
            } catch (Throwable t) {
                connection.rollback();
                throw t;
            } finally {
                /*Turn off the transaction for the connection*/
                connection.setAutoCommit(true);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @PatchMapping("/{lecturer-id}")
    public void updateLecturerDetails(){
        System.out.println("updateLecturerDetails()");
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{lecturer-id}")
    public void deleteLecturer(@PathVariable("lecturer-id") int lecturerId){
        try(Connection connection = pool.getConnection()) {
            /*Search existence of the lecturer*/
            PreparedStatement stmExist = connection.prepareStatement("SELECT * FROM lecturer WHERE id = ?");
            stmExist.setInt(1,lecturerId);
            if (!stmExist.executeQuery().next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

            /*Turn on the transaction for the connection*/
            connection.setAutoCommit(false);
            try {
                /*Identify the type of the lecturer as well as the rank*/
                PreparedStatement stmIdentify = connection.prepareStatement("SELECT l.id, l.name, l.picture, ftr.`rank` AS ftr, ptr.`rank` " +
                        "AS ptr FROM lecturer l LEFT OUTER JOIN full_time_rank ftr ON l.id = ftr.lecturer_id " +
                        "LEFT OUTER JOIN part_time_rank ptr ON l.id = ptr.lecturer_id WHERE l.id = ?");
                stmIdentify.setInt(1,lecturerId);
                ResultSet rst = stmIdentify.executeQuery();
                rst.next();
                int ftr = rst.getInt("ftr");
                int ptr = rst.getInt("ptr");
                String picture = rst.getString("picture");
                String tableName = ftr > 0 ? "full_time_rank" : "part_time_rank";
                int rank = ftr > 0 ? ftr : ptr;

                /*Delete lecturer from type table*/
                Statement stmDeleteRank = connection.createStatement();
                stmDeleteRank.executeUpdate("DELETE FROM " + tableName + " WHERE `rank` = " + rank);

                /*Update other lecturer's rank after removal process*/
                Statement stmShift = connection.createStatement();
                stmShift.executeUpdate("UPDATE " + tableName + " SET `rank` = `rank` - 1 WHERE `rank` > " + rank);

                /*Delete lecturer from main table*/
                PreparedStatement stmDeleteLecturer = connection.prepareStatement("DELETE FROM lecturer WHERE id = ?");
                stmDeleteLecturer.setInt(1,lecturerId);
                stmDeleteLecturer.executeUpdate();

                /*Delete picture of the lecturer from firebase database*/
                if (picture != null) bucket.get(picture).delete();

                connection.commit();
            } catch (Throwable t) {
                connection.rollback();
                throw t;
            } finally {
                /*Turn off the transaction for the connection*/
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping
    public void getAllLecturers(){
        System.out.println("getAllLecturers()");
    }
}
