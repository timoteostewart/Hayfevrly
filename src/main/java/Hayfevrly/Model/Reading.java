package Hayfevrly.Model;

import java.time.LocalDateTime;
import java.util.Objects;

import static Hayfevrly.Model.Spreadsheet.getCommonNameOfAnyIdentifier;

public class Reading implements Comparable<Reading> {

    String originating_entity;
    String immediate_source;

    LocalDateTime when_acquired_ldt; // this value should be in Central time
    LocalDateTime when_published_ldt; // this value should be in Central time

    String allergen_identifier;
    Integer measurement_scalar;
    String measurement_unit;

    levelDescriptorCanonical level_descriptor_canonical;

    public Reading(String allergen_identifier) {
        this.allergen_identifier = allergen_identifier;
        this.measurement_unit = "gr/m³";
    }

    public LocalDateTime getWhen_published_ldt() {
        return when_published_ldt;
    }

    public void setWhen_published_ldt(LocalDateTime when_published_ldt) {
        this.when_published_ldt = when_published_ldt;
    }

    public String getImmediate_source() {
        return immediate_source;
    }

    public void setImmediate_source(String immediate_source) {
        this.immediate_source = immediate_source;
    }

    public String getOriginating_entity() {
        return originating_entity;
    }

    public void setOriginating_entity(String originating_entity) {
        this.originating_entity = originating_entity;
    }

    public LocalDateTime getWhen_acquired_ldt() {
        return when_acquired_ldt;
    }

    public void setWhen_acquired_ldt(LocalDateTime when_acquired_ldt) {
        this.when_acquired_ldt = when_acquired_ldt;
    }

    public String getAllergen_identifier() {
        return allergen_identifier;
    }

    public void setAllergen_identifier(String allergen_identifier) {
        this.allergen_identifier = allergen_identifier;
    }

    public Integer getMeasurement_scalar() {
        return measurement_scalar;
    }

    public void setMeasurement_scalar(Integer measurement_scalar) {
        this.measurement_scalar = measurement_scalar;
    }

    public void setMeasurement_scalar(int measurement_scalar) {
        this.measurement_scalar = measurement_scalar;
    }

    public String getMeasurement_unit() {
        return measurement_unit;
    }

    public void setMeasurement_unit(String measurement_unit) {
        this.measurement_unit = measurement_unit;
    }

    public levelDescriptorCanonical getLevel_descriptor_canonical() {
        return level_descriptor_canonical;
    }

    public void setLevel_descriptor_canonical(levelDescriptorCanonical level_descriptor_canonical) {
        this.level_descriptor_canonical = level_descriptor_canonical;
    }

    @Override
    public int compareTo(Reading r) {
        return this.allergen_identifier.compareTo(r.allergen_identifier);
    }

    @Override
    public int hashCode() {
        // inspired by Bloch's Effective Java (2/e), item 8
        int prime = 31;
        int result = 17; // also helpful to initialize this as a prime
        result = result * prime + originating_entity.hashCode();
        result = result * prime + immediate_source.hashCode();
        result = result * prime + (when_published_ldt == null ? 0 : when_published_ldt.hashCode());
        result = result * prime + allergen_identifier.hashCode();
        result = result * prime + (measurement_scalar == null ? 0 : measurement_scalar.hashCode());
        result = result * prime + (measurement_unit == null ? 0 : measurement_unit.hashCode());
        result = result * prime + (level_descriptor_canonical == null ? 0 : level_descriptor_canonical.name().hashCode());
        /*
        Note that we ignore these three fields when computing hashCode():
            • LocalDateTime when_acquired_ldt
            • String raw_reading_string
            • LocalDateTime parsed_datetime;
        */
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Reading)) {
            return false;
        }
        Reading lhs = (Reading) o;
        return originating_entity.equals(lhs.originating_entity)
                && immediate_source.equals(lhs.immediate_source)
                && (Objects.equals(when_acquired_ldt, lhs.when_acquired_ldt))
                && (Objects.equals(when_published_ldt, lhs.when_published_ldt))
                && allergen_identifier.equals(lhs.allergen_identifier)
                && (Objects.equals(measurement_scalar, lhs.measurement_scalar))
                && (Objects.equals(measurement_unit, lhs.measurement_unit))
                && (Objects.equals(level_descriptor_canonical, lhs.level_descriptor_canonical));
    }

    @Override
    public String toString() {
        return allergen_identifier + " (" + getCommonNameOfAnyIdentifier(this.allergen_identifier) + ") " + measurement_scalar + " " + measurement_unit + " " + level_descriptor_canonical;
    }

    public enum levelDescriptorCanonical {
        UNCERTAIN {
                    @Override
                    public String toString() {
                        return "Uncertain";
                    }
                },
        NOT_YET_SET {
                    @Override
                    public String toString() {
                        return "Not yet set";
                    }
                },
        ABSENT {
                    @Override
                    public String toString() {
                        return "Absent";
                    }
                },
        PRESENT {
                    @Override
                    public String toString() {
                        return "Present";
                    }
                },
        VERY_LOW {
                    @Override
                    public String toString() {
                        return "Very low";
                    }
                },
        LOW {
                    @Override
                    public String toString() {
                        return "Low";
                    }
                },
        MODERATE {
                    @Override
                    public String toString() {
                        return "Moderate";
                    }
                },
        HIGH {
                    @Override
                    public String toString() {
                        return "High";
                    }
                },
        VERY_HIGH {
                    @Override
                    public String toString() {
                        return "Very high";
                    }
                }
    }

}
