package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.dto.ImportResult;
import com.lottery.entity.ImportJob;
import com.lottery.entity.LotteryHistory;
import com.lottery.entity.SysUser;
import com.lottery.mapper.ImportJobMapper;
import com.lottery.mapper.LotteryHistoryMapper;
import com.lottery.mapper.SysUserMapper;
import com.lottery.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class ImportService {

    private final ImportJobMapper importJobMapper;
    private final LotteryHistoryMapper lotteryHistoryMapper;
    private final SysUserMapper sysUserMapper;

    public ImportService(ImportJobMapper importJobMapper,
                         LotteryHistoryMapper lotteryHistoryMapper,
                         SysUserMapper sysUserMapper) {
        this.importJobMapper = importJobMapper;
        this.lotteryHistoryMapper = lotteryHistoryMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public ImportResult importCsv(MultipartFile file) throws Exception {
        ImportJob job = new ImportJob();
        job.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv");
        job.setStatus("RUNNING");
        job.setTotalRows(0);
        job.setSuccessRows(0);
        job.setFailedRows(0);
        job.setCreatedBy(resolveUserId());
        importJobMapper.insert(job);

        int total = 0;
        int success = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (!headerSkipped && line.toLowerCase().contains("period")) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;
                total++;
                try {
                    LotteryHistory record = parseLine(line);
                    Long exists = lotteryHistoryMapper.selectCount(
                            new LambdaQueryWrapper<LotteryHistory>().eq(LotteryHistory::getPeriod, record.getPeriod())
                    );
                    if (exists > 0) {
                        failed++;
                        errors.append("duplicate period: ").append(record.getPeriod()).append("; ");
                        continue;
                    }
                    lotteryHistoryMapper.insert(record);
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errors.append(ex.getMessage()).append("; ");
                }
            }
        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setErrorMessage(ex.getMessage());
            job.setTotalRows(total);
            job.setSuccessRows(success);
            job.setFailedRows(failed);
            importJobMapper.updateById(job);
            throw ex;
        }

        job.setTotalRows(total);
        job.setSuccessRows(success);
        job.setFailedRows(failed);
        job.setStatus(failed == total && total > 0 ? "FAILED" : "SUCCESS");
        if (!errors.isEmpty()) {
            job.setErrorMessage(errors.substring(0, Math.min(errors.length(), 2000)));
        }
        importJobMapper.updateById(job);

        return new ImportResult(job.getId(), job.getFilename(), total, success, failed, job.getStatus());
    }

    private LotteryHistory parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid CSV line: " + line);
        }
        LotteryHistory record = new LotteryHistory();
        record.setPeriod(parts[0].trim());
        record.setDrawDate(LocalDate.parse(parts[1].trim(), DateTimeFormatter.ISO_LOCAL_DATE));
        if (parts.length >= 10) {
            record.setRedBalls(String.join(",", parts[2].trim(), parts[3].trim(), parts[4].trim(),
                    parts[5].trim(), parts[6].trim(), parts[7].trim()));
            record.setBlueBall(parts[8].trim());
        } else {
            record.setRedBalls(parts[2].trim());
            record.setBlueBall(parts[3].trim());
        }
        return record;
    }

    private Long resolveUserId() {
        String username = SecurityUtils.currentUsername();
        if (username == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
        );
        return user != null ? user.getId() : null;
    }
}
