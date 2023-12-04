package lk.ijse.dep11.edupanel.to.request;

import lk.ijse.dep11.edupanel.validator.LecturerImage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LecturerRequestTO implements Serializable {
    @NotBlank(message = "Name cannot be empty")
    @Pattern(regexp = "^[A-Za-z ]+$", message = "Invalid name: {value}")
    private String name;

    @NotBlank(message = "Designation cannot be empty")
    @Length(min = 2, message = "Invalid designation: {value}")
    private String designation;

    @NotBlank(message = "Qualifications cannot be empty")
    @Length(min = 2, message = "Invalid qualifications: {value}")
    private String qualifications;

    @NotBlank(message = "Type cannot be empty")
    @Pattern(regexp = "^(full-time|part-time)$", flags = Pattern.Flag.CASE_INSENSITIVE,message = "Invalid type")
    private String type;

    @LecturerImage
    private MultipartFile picture;

    @Pattern(regexp = "^http[s]?://.+$", message = "Invalid linkedin url")
    private String linkedin;
}
